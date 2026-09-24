package com.tinku.identidad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U1 — bio y foto del perfil público del Tutor (Spec_M1 US-7), y los datos que
 * el perfil público suma para UX-04 §2 (verificado, precio). De punta a punta:
 * HTTP + Spring Security + JPA + Flyway (V28) sobre PostgreSQL real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PerfilPublicoTutorIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired CredencialAcademicaRepository credencialRepository;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    /** PNG mínimo: la firma de 8 bytes es lo que mira la allowlist. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    private Usuario usuario(TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 42_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Jorge" + CONTADOR.get());
        u.setApellido("Martinez");
        u.setFechaNacimiento(LocalDate.of(1985, 3, 10));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
        }
        return usuarioRepository.save(u);
    }

    private String token(Usuario u) {
        return "Bearer " + jwtUtil.generateToken(u.getDni(), u.getTipo().name(),
                u.isCapacidadEstudiante(), u.isCapacidadAdultoResponsable());
    }

    private String bio(String texto) throws Exception {
        return objectMapper.writeValueAsString(Map.of("bio", texto));
    }

    private MockMultipartFile archivo(byte[] bytes) {
        return new MockMultipartFile("archivo", "foto", MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes);
    }

    @Test
    void us7_tutorCargaSuBio_yUnaFamiliaLaVeEnElPerfilPublico() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Usuario familia = usuario(TipoUsuario.ADULTO);

        mvc.perform(put("/api/tutores/me/perfil").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bio("  Profe de matemática hace 10 años. Preparo ingresos.  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Profe de matemática hace 10 años. Preparo ingresos."));

        mvc.perform(get("/api/tutores/{id}", tutor.getId()).header("Authorization", token(familia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Profe de matemática hace 10 años. Preparo ingresos."))
                .andExpect(jsonPath("$.tieneFoto").value(false))
                // El perfil público no expone capacidades, DNI ni email.
                .andExpect(jsonPath("$.capacidadEstudiante").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void us7_bioVacia_quedaSinBio() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        tutor.setBio("vieja");
        usuarioRepository.save(tutor);

        mvc.perform(put("/api/tutores/me/perfil").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON).content(bio("   ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist());
        assertThat(usuarioRepository.findById(tutor.getId()).orElseThrow().getBio()).isNull();
    }

    @Test
    void us7_bioDeMasDe500Caracteres_422_yNoSeGuarda() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);

        mvc.perform(put("/api/tutores/me/perfil").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON).content(bio("a".repeat(501))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(usuarioRepository.findById(tutor.getId()).orElseThrow().getBio()).isNull();
    }

    @Test
    void us7_unAdultoNoTienePerfilPublico_403() throws Exception {
        Usuario adulto = usuario(TipoUsuario.ADULTO);

        mvc.perform(put("/api/tutores/me/perfil").header("Authorization", token(adulto))
                        .contentType(MediaType.APPLICATION_JSON).content(bio("hola")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/tutores/me/foto").file(archivo(PNG))
                        .header("Authorization", token(adulto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void us7_fotoPng_seSirvePorContenidoReal_yElPerfilLoIndica() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Usuario familia = usuario(TipoUsuario.ADULTO);

        mvc.perform(multipart(HttpMethod.PUT, "/api/tutores/me/foto").file(archivo(PNG))
                        .header("Authorization", token(tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneFoto").value(true));

        mvc.perform(get("/api/tutores/{id}/foto", tutor.getId()).header("Authorization", token(familia)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));

        // La referencia interna del almacenamiento nunca sale en el perfil.
        String perfil = mvc.perform(get("/api/tutores/{id}", tutor.getId()).header("Authorization", token(familia)))
                .andReturn().getResponse().getContentAsString();
        assertThat(perfil).doesNotContain("file:").doesNotContain("fotoRef");

        mvc.perform(delete("/api/tutores/me/foto").header("Authorization", token(tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneFoto").value(false));
        mvc.perform(get("/api/tutores/{id}/foto", tutor.getId()).header("Authorization", token(familia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void us7_unPdfNoEsUnaFoto_422_yNoSeGuarda() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);

        mvc.perform(multipart(HttpMethod.PUT, "/api/tutores/me/foto").file(archivo(PDF))
                        .header("Authorization", token(tutor)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(usuarioRepository.findById(tutor.getId()).orElseThrow().getFotoRef()).isNull();
    }

    @Test
    void us7_elAdminDeModeracionQuitaBioYFoto_ySoporteFinancieroNoPuede() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        tutor.setBio("Escribime al 11-5555-5555");
        tutor.setFotoRef("file:/no/importa");
        usuarioRepository.save(tutor);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);

        mvc.perform(delete("/api/admin/moderacion/tutores/{id}/bio", tutor.getId())
                        .header("Authorization", token(soporte)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/moderacion/tutores/{id}/bio", tutor.getId())
                        .header("Authorization", token(moderador)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/moderacion/tutores/{id}/foto", tutor.getId())
                        .header("Authorization", token(moderador)))
                .andExpect(status().isNoContent());

        Usuario moderado = usuarioRepository.findById(tutor.getId()).orElseThrow();
        assertThat(moderado.getBio()).isNull();
        assertThat(moderado.getFotoRef()).isNull();
    }

    @Test
    void perfilPublico_diceSiEstaVerificado_yMuestraElPrecioQueConfiguro() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Usuario familia = usuario(TipoUsuario.ADULTO);

        mvc.perform(get("/api/tutores/{id}", tutor.getId()).header("Authorization", token(familia)))
                .andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.precioHora").doesNotExist());
        mvc.perform(get("/api/pagos/tarifa").header("Authorization", token(tutor)))
                .andExpect(status().isNoContent());

        CredencialAcademica c = new CredencialAcademica();
        c.setTutor(tutor);
        c.setTipoDocumento(TipoCredencial.TITULO);
        c.setArchivoUrl("file:/no/importa");
        c.setEstado(EstadoCredencial.APROBADO);
        c.setNumeroIntento(1);
        credencialRepository.save(c);
        mvc.perform(put("/api/pagos/tarifa").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioHora\": 18000}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tutores/{id}", tutor.getId()).header("Authorization", token(familia)))
                .andExpect(jsonPath("$.verificado").value(true))
                .andExpect(jsonPath("$.precioHora").value(18000));
        mvc.perform(get("/api/pagos/tarifa").header("Authorization", token(tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(18000));
    }

    private Usuario admin(RolAdmin rol) {
        Usuario u = usuario(TipoUsuario.ADULTO);
        Admin fila = new Admin();
        fila.setUsuario(u);
        fila.setRol(rol);
        adminRepository.save(fila);
        return u;
    }

    @Test
    void estadoPerfil_diceQueLeFaltaAlTutor_yNoAplicaAOtrosTipos() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);

        mvc.perform(get("/api/tutores/me/estado-perfil").header("Authorization", token(tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleEnBusquedas").value(false))
                .andExpect(jsonPath("$.ultimaCredencial").doesNotExist())
                .andExpect(jsonPath("$.tienePrecio").value(false))
                .andExpect(jsonPath("$.tieneBio").value(false));

        mvc.perform(put("/api/tutores/me/perfil").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON).content(bio("Hola")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/pagos/tarifa").header("Authorization", token(tutor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioHora\": 9000}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tutores/me/estado-perfil").header("Authorization", token(tutor)))
                .andExpect(jsonPath("$.tienePrecio").value(true))
                .andExpect(jsonPath("$.tieneBio").value(true));

        mvc.perform(get("/api/tutores/me/estado-perfil").header("Authorization", token(usuario(TipoUsuario.ADULTO))))
                .andExpect(status().isForbidden());
    }
}
