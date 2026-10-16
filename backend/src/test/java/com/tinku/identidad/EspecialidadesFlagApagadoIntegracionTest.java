package com.tinku.identidad;

import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.MetodoVerificacion;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-M1-06 con el flag {@code tinku.especialidades.habilitado} en su valor por defecto (apagado):
 * nada de lo nuevo existe para afuera y la carga de credenciales es la de antes (condición del
 * dueño mientras falta el texto legal de la cláusula de consentimiento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EspecialidadesFlagApagadoIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
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
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private Usuario usuario(TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 36_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Pablo");
        u.setApellido("Sosa");
        u.setFechaNacimiento(LocalDate.of(1990, 5, 15));
        u.setTipo(tipo);
        u.setCapacidadEstudiante(tipo == TipoUsuario.ADULTO);
        u.setPasswordHash("hash");
        return usuarioRepository.save(u);
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtUtil.generateToken(u);
    }

    private org.springframework.test.web.servlet.ResultActions cargar(Usuario t, String tipo) throws Exception {
        return mvc.perform(multipart("/api/tutores/credenciales")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(Map.of("tipoDocumento", tipo,
                                "institucion", "UNC", "denominacion", "Profesor", "anioEgreso", 2015,
                                "aceptaConsultaRegistros", true))))
                .file(new MockMultipartFile("archivo", "t.png", MediaType.IMAGE_PNG_VALUE, PNG))
                .header("Authorization", bearer(t)));
    }

    @Test
    void config_informaApagado() throws Exception {
        mvc.perform(get("/api/especialidades/config").header("Authorization", bearer(usuario(TipoUsuario.TUTOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false));
    }

    @Test
    void endpointsNuevos_404() throws Exception {
        Usuario t = usuario(TipoUsuario.TUTOR);
        mvc.perform(get("/api/especialidades/materias").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tutores/me/especialidades").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/tutores/me/especialidades").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidades\":[{\"materia\":\"Matemática\",\"nivelMaximo\":\"SECUNDARIO\"}]}"))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/tutores/credenciales/prellenar")
                        .file(new MockMultipartFile("archivo", "t.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
        // La cláusula de consulta a registros no existe mientras el flag está apagado.
        mvc.perform(get("/api/usuarios/me/clausulas/CONSULTA_REGISTROS_TITULOS").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cargaDeCredencial_comoAntes_tiposNuevosNoSeAdmiten() throws Exception {
        Usuario t = usuario(TipoUsuario.TUTOR);
        cargar(t, "PROFESORADO").andExpect(status().isBadRequest());
        String json = cargar(t, "TITULO").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = objectMapper.readTree(json);
        assertThat(r.has("motivoRechazo")).isFalse();

        CredencialAcademica c = credencialRepository.findById(UUID.fromString(r.get("id").asText())).orElseThrow();
        // Los datos estructurados se ignoran con el flag apagado.
        assertThat(c.getInstitucion()).isNull();
        assertThat(c.getNombreCoincide()).isNull();
    }

    @Test
    void colaYPerfil_sinCamposNuevos_yAprobarComoAntesQuedaSoloDocumento() throws Exception {
        Usuario t = usuario(TipoUsuario.TUTOR);
        UUID id = UUID.fromString(objectMapper.readTree(cargar(t, "TITULO").andReturn()
                .getResponse().getContentAsString()).get("id").asText());

        Usuario adm = usuario(TipoUsuario.ADULTO);
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(adm);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);

        String cola = mvc.perform(get("/api/admin/moderacion/credenciales").header("Authorization", bearer(adm)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode filaCola = objectMapper.readTree(cola).get(0);
        assertThat(filaCola.has("tutorDni")).isFalse();
        assertThat(filaCola.has("pasoASeguir")).isFalse();

        mvc.perform(post("/api/admin/moderacion/credenciales/{id}/resolver", id)
                        .header("Authorization", bearer(adm))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APROBAR\"}"))
                .andExpect(status().isOk());
        assertThat(credencialRepository.findById(id).orElseThrow().getMetodoVerificacion())
                .isEqualTo(MetodoVerificacion.SOLO_DOCUMENTO);

        String perfil = mvc.perform(get("/api/tutores/{id}", t.getId()).header("Authorization", bearer(adm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(perfil).has("especialidades")).isFalse();
    }
}
