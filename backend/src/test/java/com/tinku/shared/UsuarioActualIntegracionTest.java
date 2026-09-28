package com.tinku.shared;

import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUD-036.1: el filtro JWT ya carga al Usuario para validar la cuenta y la versión de
 * credenciales; {@link UsuarioActual} lo toma del principal en lugar de volver a buscarlo.
 * Un request autenticado hace una sola consulta del usuario, no dos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UsuarioActualIntegracionTest {

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
    @Autowired JwtUtil jwtUtil;
    @MockitoSpyBean UsuarioRepository usuarioRepository;

    @Test
    void aud036_unRequestAutenticadoBuscaAlUsuarioUnaSolaVez() throws Exception {
        Usuario u = new Usuario();
        u.setDni("45000001");
        u.setNombre("Lucas");
        u.setApellido("Díaz");
        u.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        u.setTipo(TipoUsuario.ADULTO);
        u.setCapacidadEstudiante(true);
        u.setPasswordHash("hash");
        u = usuarioRepository.save(u);
        String token = jwtUtil.generateToken(u);
        clearInvocations(usuarioRepository);

        mvc.perform(get("/api/reservas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(usuarioRepository, times(1)).findById(u.getId());
    }
}
