package com.tinku.identidad;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EspecialidadTutor;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.MetodoVerificacion;
import com.tinku.identidad.model.MotivoRechazoCredencial;
import com.tinku.identidad.model.NivelEspecialidad;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.EspecialidadTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-M1-06 (T-ESP-01..04): especialidades del Tutor y verificación de títulos, con el flag
 * {@code tinku.especialidades.habilitado} encendido SOLO en esta clase (por defecto está apagado).
 *
 * <p>E1: esquema (V56) — especialidades, tipos nuevos de credencial, datos estructurados,
 * método de verificación, motivo de rechazo y qué especialidades cubre una credencial.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "tinku.especialidades.habilitado=true")
class EspecialidadesIntegracionTest {

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

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CredencialAcademicaRepository credencialRepository;
    @Autowired EspecialidadTutorRepository especialidadRepository;
    @Autowired JdbcTemplate jdbc;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private Usuario tutor() {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 37_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Valeria");
        u.setApellido("Gómez");
        u.setFechaNacimiento(LocalDate.of(1990, 3, 10));
        u.setTipo(TipoUsuario.TUTOR);
        u.setPasswordHash("hash");
        return usuarioRepository.save(u);
    }

    private EspecialidadTutor especialidad(Usuario tutor, String materia, NivelEspecialidad nivel) {
        EspecialidadTutor e = new EspecialidadTutor();
        e.setTutorId(tutor.getId());
        e.setMateria(materia);
        e.setNivelMaximo(nivel);
        return especialidadRepository.save(e);
    }

    // ------------------------------------------------------------------ E1: esquema

    @Test
    void e1_especialidadSeGuardaYNoSeRepiteLaMateria() {
        Usuario t = tutor();
        EspecialidadTutor e = especialidad(t, "Matemática", NivelEspecialidad.SECUNDARIO);

        assertThat(especialidadRepository.findByTutorIdOrderByCreatedAtAsc(t.getId()))
                .extracting(EspecialidadTutor::getId).containsExactly(e.getId());
        assertThatThrownBy(() -> especialidad(t, "Matemática", NivelEspecialidad.PRIMARIO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void e1_credencialConTipoNuevoDatosEstructuradosYCobertura() {
        Usuario t = tutor();
        EspecialidadTutor mate = especialidad(t, "Matemática", NivelEspecialidad.SECUNDARIO);

        CredencialAcademica c = new CredencialAcademica();
        c.setTutor(t);
        c.setTipoDocumento(TipoCredencial.PROFESORADO);
        c.setArchivoUrl("prof.pdf");
        c.setInstitucion("ISFD N° 41");
        c.setDenominacion("Profesora de Matemática");
        c.setAnioEgreso(2015);
        c.setEsDigital(true);
        c.setEnlaceVerificacion("https://mi.argentina.gob.ar/titulos/abc");
        c.setNombreCoincide(Boolean.FALSE);
        c.setEstado(EstadoCredencial.APROBADO);
        c.setMetodoVerificacion(MetodoVerificacion.REGISTRO_OFICIAL);
        c.setEspecialidadesCubiertas(Set.of(mate.getId()));
        UUID id = credencialRepository.save(c).getId();

        Integer cubre = jdbc.queryForObject(
                "select count(*) from identidad.credencial_especialidades where credencial_id = ? and especialidad_id = ?",
                Integer.class, id, mate.getId());
        assertThat(cubre).isEqualTo(1);

        CredencialAcademica rechazada = new CredencialAcademica();
        rechazada.setTutor(t);
        rechazada.setTipoDocumento(TipoCredencial.ALUMNO_UNIVERSITARIO);
        rechazada.setArchivoUrl("alumno.png");
        rechazada.setEstado(EstadoCredencial.RECHAZADO);
        rechazada.setMotivoRechazo(MotivoRechazoCredencial.NO_FIGURA_EN_REGISTRO);
        rechazada.setDetalleRechazo("El código de validación no existe en el Guaraní de la UNC.");
        assertThat(credencialRepository.save(rechazada).getId()).isNotNull();
    }

    @Test
    void e1_losTiposViejosSiguenAdmitidosEnLaBase() {
        // Las credenciales ya cargadas (TITULO, CERTIFICADO_ANALITICO, MATRICULA) no se tocan.
        Usuario t = tutor();
        CredencialAcademica c = new CredencialAcademica();
        c.setTutor(t);
        c.setTipoDocumento(TipoCredencial.TITULO);
        c.setArchivoUrl("viejo.pdf");
        assertThat(credencialRepository.save(c).getId()).isNotNull();
    }

    @Test
    void e1_borrarUnaEspecialidadBorraSuCobertura() {
        Usuario t = tutor();
        EspecialidadTutor mate = especialidad(t, "Física", NivelEspecialidad.UNIVERSITARIO);
        CredencialAcademica c = new CredencialAcademica();
        c.setTutor(t);
        c.setTipoDocumento(TipoCredencial.UNIVERSITARIO);
        c.setArchivoUrl("u.pdf");
        c.setEstado(EstadoCredencial.APROBADO);
        c.setMetodoVerificacion(MetodoVerificacion.REGISTRO_OFICIAL);
        c.setEspecialidadesCubiertas(Set.of(mate.getId()));
        UUID id = credencialRepository.save(c).getId();

        jdbc.update("delete from identidad.especialidades_tutor where id = ?", mate.getId());

        Integer cubre = jdbc.queryForObject(
                "select count(*) from identidad.credencial_especialidades where credencial_id = ?",
                Integer.class, id);
        assertThat(cubre).isZero();
    }
}
