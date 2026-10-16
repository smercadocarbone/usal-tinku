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
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.ocr.LectorTextoDocumento;
import com.tinku.identidad.ocr.TextoDocumento;
import com.tinku.identidad.repository.AceptacionClausulaRepository;
import com.tinku.identidad.service.ConsentimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-M1-06 (T-ESP-01..04): especialidades del Tutor y verificación de títulos, con el flag
 * {@code tinku.especialidades.habilitado} encendido SOLO en esta clase (por defecto está apagado).
 *
 * <p>E1: esquema (V56) — especialidades, tipos nuevos de credencial, datos estructurados,
 * método de verificación, motivo de rechazo y qué especialidades cubre una credencial.</p>
 *
 * <p>E2 + E4: carga de especialidades, carga estructurada con triaje (enlace, OCR, nombre),
 * cola del Admin con DNI y guía por tipo, aprobación que elige especialidades cubiertas, rechazo
 * con motivo, y las insignias por especialidad en el perfil público.</p>
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
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired AdminRepository adminRepository;
    @Autowired AceptacionClausulaRepository aceptaciones;

    @MockitoBean LectorTextoDocumento lector;

    /** PNG mínimo real (pasa la allowlist por magic bytes de AUD-007). */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private static final byte[] PDF = "%PDF-1.4 titulo de prueba".getBytes();

    private static final String TEXTO_TITULO = """
            UNIVERSIDAD NACIONAL DE CORDOBA
            Por cuanto VALERIA GOMEZ D.N.I. 37.000.001
            ha completado los estudios correspondientes
            se le otorga el titulo de PROFESORA DE MATEMATICA
            Cordoba, 12 de marzo de 2015
            """;

    @BeforeEach
    void ocrPorDefecto() {
        when(lector.leer(any())).thenReturn(TextoDocumento.leido(TEXTO_TITULO));
    }

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

    // ------------------------------------------------------------------ helpers E2

    private String token(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    private Usuario admin() {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 38_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Ada");
        u.setApellido("Admin");
        u.setFechaNacimiento(LocalDate.of(1985, 1, 1));
        u.setTipo(TipoUsuario.ADULTO);
        u.setCapacidadEstudiante(true);
        u.setPasswordHash("hash");
        usuarioRepository.save(u);
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(u);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);
        return u;
    }

    private ResultActions guardarEspecialidades(Usuario t, List<Map<String, Object>> especialidades) throws Exception {
        return mvc.perform(put("/api/tutores/me/especialidades")
                .header("Authorization", "Bearer " + token(t))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("especialidades", especialidades))));
    }

    private static Map<String, Object> esp(String materia, String nivel) {
        return Map.of("materia", materia, "nivelMaximo", nivel);
    }

    private static Map<String, Object> datos(String tipo) {
        Map<String, Object> d = new HashMap<>();
        d.put("tipoDocumento", tipo);
        d.put("institucion", "Universidad Nacional de Córdoba");
        d.put("denominacion", "Profesora de Matemática");
        d.put("anioEgreso", 2015);
        d.put("esDigital", false);
        d.put("aceptaConsultaRegistros", true);
        return d;
    }

    private ResultActions cargar(Usuario t, Map<String, Object> datos, byte[] archivo) throws Exception {
        return mvc.perform(multipart("/api/tutores/credenciales")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(datos)))
                .file(new MockMultipartFile("archivo", "titulo.png", MediaType.IMAGE_PNG_VALUE, archivo))
                .header("Authorization", "Bearer " + token(t)));
    }

    private UUID cargarOk(Usuario t, Map<String, Object> datos) throws Exception {
        String json = cargar(t, datos, PNG).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private Usuario tutorConEspecialidad(String materia, String nivel) throws Exception {
        Usuario t = tutor();
        guardarEspecialidades(t, List.of(esp(materia, nivel))).andExpect(status().isOk());
        return t;
    }

    private UUID idEspecialidad(Usuario t, String materia) {
        return especialidadRepository.findByTutorIdOrderByCreatedAtAsc(t.getId()).stream()
                .filter(e -> e.getMateria().equals(materia)).findFirst().orElseThrow().getId();
    }

    private ResultActions resolver(Usuario admin, UUID credencialId, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/admin/moderacion/credenciales/{id}/resolver", credencialId)
                .header("Authorization", "Bearer " + token(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private JsonNode perfilPublico(Usuario t) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/tutores/{id}", t.getId())
                        .header("Authorization", "Bearer " + token(tutor())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ E2: flag y catálogo

    @Test
    void e2_configInformaElFlagEncendido_yLasMateriasSalenDelCatalogo() throws Exception {
        Usuario t = tutor();
        mvc.perform(get("/api/especialidades/config").header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true));
        String json = mvc.perform(get("/api/especialidades/materias").header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json).valueStream().map(JsonNode::asText).toList())
                .contains("Matemática", "Lengua y Literatura").doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------------ E2: especialidades

    @Test
    void e2_tutorDeclaraDeUnaATresEspecialidades_quedanDeclaradas() throws Exception {
        Usuario t = tutor();
        guardarEspecialidades(t, List.of(esp("Matemática", "SECUNDARIO"), esp("Física", "UNIVERSITARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].materia").value("Matemática"))
                .andExpect(jsonPath("$[0].estado").value("DECLARADA"));

        mvc.perform(get("/api/tutores/me/especialidades").header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void e4_especialidadesInvalidas_422() throws Exception {
        Usuario t = tutor();
        guardarEspecialidades(t, List.of()).andExpect(status().isUnprocessableEntity());
        guardarEspecialidades(t, List.of(esp("Matemática", "PRIMARIO"), esp("Física", "SECUNDARIO"),
                esp("Química", "SECUNDARIO"), esp("Biología", "SECUNDARIO")))
                .andExpect(status().isUnprocessableEntity());
        guardarEspecialidades(t, List.of(esp("Matemática", "PRIMARIO"), esp("Matemática", "SECUNDARIO")))
                .andExpect(status().isUnprocessableEntity());
        guardarEspecialidades(t, List.of(esp("Astrología", "SECUNDARIO")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(especialidadRepository.findByTutorIdOrderByCreatedAtAsc(t.getId())).isEmpty();
    }

    @Test
    void e4_soloUnTutorDeclaraEspecialidades_403() throws Exception {
        Usuario adulto = admin();
        guardarEspecialidades(adulto, List.of(esp("Matemática", "SECUNDARIO")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ E2: carga estructurada

    @Test
    void e2_cargaEstructurada_guardaDatos_registraConsentimiento_yNombreCoincide() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        UUID id = cargarOk(t, datos("PROFESORADO"));

        CredencialAcademica c = credencialRepository.findById(id).orElseThrow();
        assertThat(c.getTipoDocumento()).isEqualTo(TipoCredencial.PROFESORADO);
        assertThat(c.getInstitucion()).isEqualTo("Universidad Nacional de Córdoba");
        assertThat(c.getDenominacion()).isEqualTo("Profesora de Matemática");
        assertThat(c.getAnioEgreso()).isEqualTo(2015);
        assertThat(c.getNombreCoincide()).isTrue();
        assertThat(c.getEstado()).isEqualTo(EstadoCredencial.PENDIENTE);
        assertThat(aceptaciones.findAll()).anyMatch(a -> a.getUsuarioId().equals(t.getId())
                && a.getClausula().equals(ConsentimientoService.CONSULTA_REGISTROS));
    }

    @Test
    void e4_nombreNoCoincide_noRechaza_marcaParaElAdmin() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        when(lector.leer(any())).thenReturn(TextoDocumento.leido(
                TEXTO_TITULO.replace("VALERIA GOMEZ", "MARTIN RODRIGUEZ")));
        UUID id = cargarOk(t, datos("PROFESORADO"));
        assertThat(credencialRepository.findById(id).orElseThrow().getNombreCoincide()).isFalse();
    }

    @Test
    void e4_pdfSinTextoLegibleParaElOcr_noRechaza_yNoSeComparaElNombre() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        when(lector.leer(any())).thenReturn(TextoDocumento.noProcesable());
        String json = mvc.perform(multipart("/api/tutores/credenciales")
                        .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(datos("UNIVERSITARIO"))))
                        .file(new MockMultipartFile("archivo", "t.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))
                        .header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(json).get("id").asText());
        assertThat(credencialRepository.findById(id).orElseThrow().getNombreCoincide()).isNull();
    }

    @Test
    void e4_triaje_rechazoInmediatoConMotivo_sinGuardarNiConsumirIntento() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");

        // Imagen sin texto legible.
        when(lector.leer(any())).thenReturn(TextoDocumento.sinTexto());
        cargar(t, datos("PROFESORADO"), PNG).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No pudimos leer")));

        // Un DNI en lugar de un título.
        when(lector.leer(any())).thenReturn(TextoDocumento.leido("""
                REPUBLICA ARGENTINA - MERCOSUR
                REGISTRO NACIONAL DE LAS PERSONAS
                DOCUMENTO NACIONAL DE IDENTIDAD
                IDARG37000001<1<<<<<<<<<<<<<<<
                """));
        cargar(t, datos("PROFESORADO"), PNG).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("DNI")));

        // Una captura de otra cosa.
        when(lector.leer(any())).thenReturn(TextoDocumento.leido("Lista del super: pan, leche, yerba"));
        cargar(t, datos("PROFESORADO"), PNG).andExpect(status().isUnprocessableEntity());

        assertThat(credencialRepository.findFirstByTutorIdOrderByCreatedAtDesc(t.getId())).isEmpty();
    }

    @Test
    void e4_enlaceFueraDeLaAllowlist_rechazoInmediato() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        for (String enlace : List.of("http://registrograduados.siu.edu.ar/x",
                "https://registrograduados.siu.edu.ar.evil.com/x", "https://titulos-truchos.com/qr")) {
            Map<String, Object> d = datos("PROFESORADO");
            d.put("esDigital", true);
            d.put("enlaceVerificacion", enlace);
            cargar(t, d, PNG).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("https://")));
        }
        assertThat(credencialRepository.findFirstByTutorIdOrderByCreatedAtDesc(t.getId())).isEmpty();
    }

    @Test
    void e4_enlaceObligatorio_digitalOAlumnoRegular() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        Map<String, Object> digital = datos("SECUNDARIO");
        digital.put("esDigital", true);
        cargar(t, digital, PNG).andExpect(status().isUnprocessableEntity());
        cargar(t, datos("ALUMNO_UNIVERSITARIO"), PNG).andExpect(status().isUnprocessableEntity());

        Map<String, Object> conEnlace = datos("ALUMNO_UNIVERSITARIO");
        conEnlace.put("enlaceVerificacion", "https://guarani.unc.edu.ar/validar?codigo=ABC123");
        cargarOk(t, conEnlace);
    }

    @Test
    void e4_cargaSinConsentimientoSinEspecialidadOConTipoViejo_seRechaza() throws Exception {
        Usuario sinEsp = tutor();
        cargar(sinEsp, datos("PROFESORADO"), PNG).andExpect(status().isUnprocessableEntity());

        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        Map<String, Object> sinConsentimiento = datos("PROFESORADO");
        sinConsentimiento.put("aceptaConsultaRegistros", false);
        cargar(t, sinConsentimiento, PNG).andExpect(status().isUnprocessableEntity());

        Map<String, Object> sinInstitucion = datos("PROFESORADO");
        sinInstitucion.remove("institucion");
        cargar(t, sinInstitucion, PNG).andExpect(status().isUnprocessableEntity());

        // Con el flag encendido, los tipos viejos ya no se cargan.
        cargar(t, datos("TITULO"), PNG).andExpect(status().isBadRequest());
        assertThat(credencialRepository.findFirstByTutorIdOrderByCreatedAtDesc(t.getId())).isEmpty();
    }

    @Test
    void e2_prellenadoPorOcr() throws Exception {
        Usuario t = tutor();
        mvc.perform(multipart("/api/tutores/credenciales/prellenar")
                        .file(new MockMultipartFile("archivo", "t.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institucion").value("UNIVERSIDAD NACIONAL DE CORDOBA"))
                .andExpect(jsonPath("$.denominacion").value("PROFESORA DE MATEMATICA"))
                .andExpect(jsonPath("$.anioEgreso").value(2015));
    }

    // ------------------------------------------------------------------ E2: cola y decisión del Admin

    @Test
    void e2_colaDelAdmin_muestraDniDatosMarcaYPasoASeguir() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        Map<String, Object> d = datos("UNIVERSITARIO");
        d.put("enlaceVerificacion", "https://registrograduados.siu.edu.ar/consulta");
        UUID id = cargarOk(t, d);

        String json = mvc.perform(get("/api/admin/moderacion/credenciales")
                        .header("Authorization", "Bearer " + token(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fila = objectMapper.readTree(json).valueStream()
                .filter(n -> n.get("id").asText().equals(id.toString())).findFirst().orElseThrow();
        assertThat(fila.get("tutorDni").asText()).isEqualTo(t.getDni());
        assertThat(fila.get("institucion").asText()).isEqualTo("Universidad Nacional de Córdoba");
        assertThat(fila.get("anioEgreso").asInt()).isEqualTo(2015);
        assertThat(fila.get("enlaceVerificacion").asText()).isEqualTo("https://registrograduados.siu.edu.ar/consulta");
        assertThat(fila.get("nombreCoincide").asBoolean()).isTrue();
        assertThat(fila.get("pasoASeguir").asText()).contains("Registro Público de Graduados");
        assertThat(fila.get("nivelMaximoQueRespalda").asText()).isEqualTo("UNIVERSITARIO");
        assertThat(fila.get("especialidades").get(0).get("materia").asText()).isEqualTo("Matemática");
    }

    @Test
    void e2_aprobarContraRegistroOficial_especialidadVerificada_enPerfilYMatching() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        guardarEspecialidades(t, List.of(esp("Matemática", "SECUNDARIO"), esp("Física", "SECUNDARIO")))
                .andExpect(status().isOk());
        UUID id = cargarOk(t, datos("PROFESORADO"));

        resolver(admin(), id, Map.of("decision", "APROBAR", "metodoVerificacion", "REGISTRO_OFICIAL",
                "especialidadesCubiertas", List.of(idEspecialidad(t, "Matemática"))))
                .andExpect(status().isOk());

        CredencialAcademica c = credencialRepository.findById(id).orElseThrow();
        assertThat(c.getMetodoVerificacion()).isEqualTo(MetodoVerificacion.REGISTRO_OFICIAL);
        assertThat(usuarioRepository.findById(t.getId()).orElseThrow().isActivoParaMatching()).isTrue();

        JsonNode perfil = perfilPublico(t);
        Map<String, String> estados = new HashMap<>();
        perfil.get("especialidades").forEach(e -> estados.put(e.get("materia").asText(), e.get("estado").asText()));
        assertThat(estados).containsEntry("Matemática", "VERIFICADA").containsEntry("Física", "DECLARADA");
    }

    @Test
    void e2_aprobarSoloDocumento_quedaConRespaldoDocumental() throws Exception {
        Usuario t = tutorConEspecialidad("Lengua", "PRIMARIO");
        UUID id = cargarOk(t, datos("SECUNDARIO"));
        resolver(admin(), id, Map.of("decision", "APROBAR", "metodoVerificacion", "SOLO_DOCUMENTO",
                "especialidadesCubiertas", List.of(idEspecialidad(t, "Lengua"))))
                .andExpect(status().isOk());
        assertThat(perfilPublico(t).get("especialidades").get(0).get("estado").asText())
                .isEqualTo("RESPALDO_DOCUMENTAL");
    }

    @Test
    void e4_credencialQueNoCubreLaEspecialidad_422() throws Exception {
        // Un secundario respalda hasta primario: no puede cubrir Matemática de secundario.
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        UUID id = cargarOk(t, datos("SECUNDARIO"));
        Usuario adm = admin();
        resolver(adm, id, Map.of("decision", "APROBAR", "metodoVerificacion", "SOLO_DOCUMENTO",
                "especialidadesCubiertas", List.of(idEspecialidad(t, "Matemática"))))
                .andExpect(status().isUnprocessableEntity());

        // Una especialidad de otro Tutor tampoco.
        Usuario otro = tutorConEspecialidad("Matemática", "PRIMARIO");
        resolver(adm, id, Map.of("decision", "APROBAR", "metodoVerificacion", "SOLO_DOCUMENTO",
                "especialidadesCubiertas", List.of(idEspecialidad(otro, "Matemática"))))
                .andExpect(status().isUnprocessableEntity());

        // Sin decir cómo verificó, tampoco.
        resolver(adm, id, Map.of("decision", "APROBAR")).andExpect(status().isUnprocessableEntity());

        assertThat(credencialRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoCredencial.PENDIENTE);
    }

    @Test
    void e2_rechazoConMotivo_elTutorLoVe() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        UUID id = cargarOk(t, datos("UNIVERSITARIO"));
        Usuario adm = admin();

        resolver(adm, id, Map.of("decision", "RECHAZAR")).andExpect(status().isUnprocessableEntity());
        resolver(adm, id, Map.of("decision", "RECHAZAR", "motivoRechazo", "OTRO"))
                .andExpect(status().isUnprocessableEntity());
        resolver(adm, id, Map.of("decision", "RECHAZAR", "motivoRechazo", "NO_FIGURA_EN_REGISTRO",
                "detalleRechazo", "Con tu DNI no figura ningún título de la UNC."))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tutores/me/credencial").header("Authorization", "Bearer " + token(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivoRechazo").value("NO_FIGURA_EN_REGISTRO"))
                .andExpect(jsonPath("$.motivoRechazoTexto").value("No encontramos el título en el registro oficial."))
                .andExpect(jsonPath("$.detalleRechazo").value("Con tu DNI no figura ningún título de la UNC."));
    }

    @Test
    void e4_cambiarElNivelDeUnaEspecialidadVerificada_pierdeLaVerificacion() throws Exception {
        Usuario t = tutorConEspecialidad("Matemática", "SECUNDARIO");
        UUID id = cargarOk(t, datos("PROFESORADO"));
        resolver(admin(), id, Map.of("decision", "APROBAR", "metodoVerificacion", "REGISTRO_OFICIAL",
                "especialidadesCubiertas", List.of(idEspecialidad(t, "Matemática"))))
                .andExpect(status().isOk());

        // Misma materia y nivel: sigue verificada.
        guardarEspecialidades(t, List.of(esp("Matemática", "SECUNDARIO"), esp("Física", "PRIMARIO")))
                .andExpect(jsonPath("$[0].estado").value("VERIFICADA"));
        // Sube a universitario: el profesorado no lo respalda, vuelve a declarada.
        guardarEspecialidades(t, List.of(esp("Matemática", "UNIVERSITARIO")))
                .andExpect(jsonPath("$[0].estado").value("DECLARADA"));
    }

    // ------------------------------------------------------------------ E4: por tipo (SPIKE §4.3)

    /**
     * Cada tipo nuevo se carga, llega a la cola con su paso a seguir y respalda hasta el nivel de la
     * tabla: cubrir una especialidad en ese nivel la verifica; por encima, 422. Los tipos que la
     * tabla no menciona no respaldan ninguna (se aprueban sin cobertura).
     */
    @ParameterizedTest(name = "{0}: respalda hasta {1}")
    @CsvSource({
            "SECUNDARIO,PRIMARIO,SECUNDARIO",
            "ALUMNO_UNIVERSITARIO,SECUNDARIO,UNIVERSITARIO",
            "PROFESORADO,SECUNDARIO,UNIVERSITARIO",
            "UNIVERSITARIO,UNIVERSITARIO,",
            "MATRICULA_PROFESIONAL,UNIVERSITARIO,",
            "TERCIARIO_NO_DOCENTE,,PRIMARIO",
            "OTRO,,PRIMARIO"
    })
    void e4_porTipo_cargaColaYCoberturaSegunLaTabla(String tipo, String tope, String porEncima) throws Exception {
        Usuario t = tutor();
        List<Map<String, Object>> esps = new java.util.ArrayList<>();
        if (tope != null) esps.add(esp("Matemática", tope));
        if (porEncima != null) esps.add(esp("Física", porEncima));
        guardarEspecialidades(t, esps).andExpect(status().isOk());

        Map<String, Object> d = datos(tipo);
        if (tipo.equals("ALUMNO_UNIVERSITARIO")) d.put("enlaceVerificacion", "SIU-ABC123");
        UUID id = cargarOk(t, d);
        Usuario adm = admin();

        String cola = mvc.perform(get("/api/admin/moderacion/credenciales").header("Authorization", "Bearer " + token(adm)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode fila = objectMapper.readTree(cola).valueStream()
                .filter(n -> n.get("id").asText().equals(id.toString())).findFirst().orElseThrow();
        assertThat(fila.get("pasoASeguir").asText()).isNotBlank();
        if (tope == null) {
            assertThat(fila.has("nivelMaximoQueRespalda")).isFalse();
        } else {
            assertThat(fila.get("nivelMaximoQueRespalda").asText()).isEqualTo(tope);
        }

        if (porEncima != null) {
            resolver(adm, id, Map.of("decision", "APROBAR", "metodoVerificacion", "REGISTRO_OFICIAL",
                    "especialidadesCubiertas", List.of(idEspecialidad(t, "Física"))))
                    .andExpect(status().isUnprocessableEntity());
        }
        List<UUID> cubre = tope == null ? List.of() : List.of(idEspecialidad(t, "Matemática"));
        resolver(adm, id, Map.of("decision", "APROBAR", "metodoVerificacion", "REGISTRO_OFICIAL",
                "especialidadesCubiertas", cubre))
                .andExpect(status().isOk());

        Map<String, String> estados = new HashMap<>();
        perfilPublico(t).get("especialidades")
                .forEach(e -> estados.put(e.get("materia").asText(), e.get("estado").asText()));
        if (tope != null) assertThat(estados).containsEntry("Matemática", "VERIFICADA");
        if (porEncima != null) assertThat(estados).containsEntry("Física", "DECLARADA");
    }
}
