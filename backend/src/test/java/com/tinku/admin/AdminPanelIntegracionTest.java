package com.tinku.admin;

import tools.jackson.databind.ObjectMapper;
import com.tinku.admin.model.LogAuditoriaAdmin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.admin.repository.LogAuditoriaAdminRepository;
import com.tinku.admin.repository.TicketSoporteRepository;
import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoParcialProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.PrecioReferenciaRegionalRepository;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.MotivoDenuncia;
import com.tinku.seguridad.repository.DenunciaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Chunk M8 — panel de administración de punta a punta (HTTP real + BD real):
 * colas de Moderación (orden por plazo de resolución, T-M8-04), cola de Soporte
 * Financiero + reintento manual reusando M5 (T-M8-04), tickets enrutados por
 * {@code mapeo_origen_rol} (T-M8-05), tabla de precios regional con versionado
 * y, transversal, la auditoría append-only de cada acción 2xx (T-M8-02, US-6)
 * y el 403 cruzado de roles contra {@code admin.admins} (T-M8-03/06).
 *
 * Los Admin se siembran como fila en {@code admin.admins} (el gate ya no lee
 * allowlist); los datos de cola se siembran directo en BD, patrón del resto de
 * los integracion tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminPanelIntegracionTest {

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
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired PrecioReferenciaRegionalRepository precioRepository;
    @Autowired TicketSoporteRepository ticketRepository;
    @Autowired LogAuditoriaAdminRepository auditoriaRepository;
    @Autowired com.tinku.identidad.port.Almacenamiento almacenamiento;

    @MockitoBean LiberacionProveedor liberacion;
    @MockitoBean ReembolsoProveedor reembolso;
    @MockitoBean ReembolsoParcialProveedor reembolsoParcial;
    @MockitoBean AlertaSoporteProveedor alertaSoporte;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    // ---------------------------------------------------------------- helpers

    private Usuario usuario(TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 41_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Nombre" + CONTADOR.get());
        u.setApellido("Lopez");
        u.setFechaNacimiento(LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    private Usuario admin(RolAdmin rol) {
        Usuario u = usuario(TipoUsuario.ADULTO);
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(u);
        fila.setRol(rol);
        adminRepository.save(fila);
        return u;
    }

    private String token(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    private CredencialAcademica credencial(Usuario tutor, EstadoCredencial estado,
                                           Instant cicloEsperaHasta) {
        CredencialAcademica c = new CredencialAcademica();
        c.setTutor(tutor);
        c.setTipoDocumento(TipoCredencial.TITULO);
        c.setArchivoUrl("https://cdn.test/" + UUID.randomUUID() + ".pdf");
        c.setEstado(estado);
        c.setNumeroIntento(1);
        c.setCicloEsperaHasta(cicloEsperaHasta);
        return credencialRepository.save(c);
    }

    private Transaccion transaccion(EstadoTransaccion estado, int intentos) {
        Reserva reserva = new Reserva();
        reserva.setPagador(usuario(TipoUsuario.ADULTO));
        reserva.setBeneficiario(reserva.getPagador());
        reserva.setTutor(usuario(TipoUsuario.TUTOR));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion t = new Transaccion();
        t.setReservaId(reserva.getId());
        t.setMpPaymentId("mp-" + CONTADOR.incrementAndGet());
        t.setMontoBruto(BigDecimal.valueOf(15000));
        t.setComisionPlataforma(new BigDecimal("2250.00"));
        t.setEstado(estado);
        t.setIntentosLiberacion(intentos);
        return transaccionRepository.save(t);
    }

    /** Reserva confirmada + su Sesión — requisito previo de alertas/transacciones (FK). */
    private UUID sesionConReserva() {
        Reserva reserva = new Reserva();
        reserva.setPagador(usuario(TipoUsuario.ADULTO));
        reserva.setBeneficiario(reserva.getPagador());
        reserva.setTutor(usuario(TipoUsuario.TUTOR));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);
        SesionAprendizaje sesion = new SesionAprendizaje();
        sesion.setReservaId(reserva.getId());
        return sesionRepository.save(sesion).getId();
    }

    private List<UUID> idsDe(String json) throws Exception {
        return java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(json).spliterator(), false)
                .map(n -> UUID.fromString(n.get("id").asText())).toList();
    }

    private List<LogAuditoriaAdmin> auditoriaDe(String accion) {
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getAccion().equals(accion)).toList();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void colasCredenciales_ordenadasPorCicloEspera_nullAlFinal_auditadas_403Cruzados()
            throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario usuarioComun = usuario(TipoUsuario.ADULTO);

        Usuario t1 = usuario(TipoUsuario.TUTOR);
        Usuario t2 = usuario(TipoUsuario.TUTOR);
        Usuario t3 = usuario(TipoUsuario.TUTOR);
        Usuario t4 = usuario(TipoUsuario.TUTOR);

        // Pendientes a revisar, ordenadas por urgencia: la de ventana VENCIDA
        // primero (se puede revisar ya), luego ciclo más corto → más largo, y
        // las sin ciclo al final (nulls last).
        CredencialAcademica cVencida = credencial(t1, EstadoCredencial.PENDIENTE,
                Instant.now().minusSeconds(3600));
        CredencialAcademica cT2 = credencial(t2, EstadoCredencial.PENDIENTE,
                Instant.now().plusSeconds(3600));
        CredencialAcademica cT1 = credencial(t3, EstadoCredencial.PENDIENTE,
                Instant.now().plusSeconds(7200));
        CredencialAcademica cSinCiclo = credencial(t4, EstadoCredencial.PENDIENTE, null);
        // Una NO pendiente nunca entra a la cola.
        credencial(t1, EstadoCredencial.RECHAZADO, null);

        String json = mvc.perform(get("/api/admin/moderacion/credenciales")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        List<UUID> ids = idsDe(json);
        assertThat(ids).containsExactly(
                cVencida.getId(), cT2.getId(), cT1.getId(), cSinCiclo.getId());

        // 403 de rol cruzado y de simple usuario — mismo 403 que M7/M9.
        mvc.perform(get("/api/admin/moderacion/credenciales")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/moderacion/credenciales")
                        .header("Authorization", "Bearer " + token(usuarioComun)))
                .andExpect(status().isForbidden());

        // US-6: la acción quedó auditada con el Admin que la ejecutó (admins.id).
        List<LogAuditoriaAdmin> auditadas = auditoriaDe("GET /api/admin/moderacion/credenciales");
        assertThat(auditadas).hasSize(1);
        assertThat(auditadas.get(0).getAdminId()).isNotNull();
        assertThat(auditadas.get(0).getEntidadTipo()).isEqualTo("credenciales");
    }

    // ------------------------------------------------ AUD-007: ver el archivo de la credencial

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(
            java.nio.charset.StandardCharsets.UTF_8);
    // Las fixtures usan APROBADO: el endpoint no mira el estado, y una PENDIENTE
    // ensuciaría la cola que colasCredenciales_* afirma completa (BD compartida).

    @Test
    void aud007_moderadorVeElArchivoDeLaCredencial_bytesTipoYAuditoria() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        CredencialAcademica c = credencial(usuario(TipoUsuario.TUTOR), EstadoCredencial.APROBADO, null);
        c.setArchivoUrl(almacenamiento.guardar(PDF, "titulo.pdf"));
        credencialRepository.save(c);

        mvc.perform(get("/api/admin/moderacion/credenciales/{id}/archivo", c.getId())
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));

        // Ver el documento de identidad académica de alguien es una acción auditable.
        List<LogAuditoriaAdmin> auditadas = auditoriaDe(
                "GET /api/admin/moderacion/credenciales/" + c.getId() + "/archivo");
        assertThat(auditadas).hasSize(1);
        assertThat(auditadas.get(0).getEntidadId()).isEqualTo(c.getId().toString());
    }

    @Test
    void aud007_soporteFinancieroYUsuarioComun_403() throws Exception {
        CredencialAcademica c = credencial(usuario(TipoUsuario.TUTOR), EstadoCredencial.APROBADO, null);
        c.setArchivoUrl(almacenamiento.guardar(PDF, "titulo.pdf"));
        credencialRepository.save(c);

        mvc.perform(get("/api/admin/moderacion/credenciales/{id}/archivo", c.getId())
                        .header("Authorization", "Bearer " + token(admin(RolAdmin.SOPORTE_FINANCIERO))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/moderacion/credenciales/{id}/archivo", c.getId())
                        .header("Authorization", "Bearer " + token(usuario(TipoUsuario.ADULTO))))
                .andExpect(status().isForbidden());
        // El propio Tutor tampoco: el endpoint es de moderación, no de consulta propia.
        mvc.perform(get("/api/admin/moderacion/credenciales/{id}/archivo", c.getId())
                        .header("Authorization", "Bearer " + token(c.getTutor())))
                .andExpect(status().isForbidden());
    }

    @Test
    void aud007_archivoUrlFueraDelAlmacenamiento_noSeSirve() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        CredencialAcademica c = credencial(usuario(TipoUsuario.TUTOR), EstadoCredencial.APROBADO, null);
        c.setArchivoUrl("file:///etc/hosts"); // fila corrompida o manipulada
        credencialRepository.save(c);

        mvc.perform(get("/api/admin/moderacion/credenciales/{id}/archivo", c.getId())
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isNotFound());
    }

    @Test
    void colasAlertasYDenuncias_ordenadasPorUrgenciaReal() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario tutor = usuario(TipoUsuario.TUTOR);

        // Denuncias en_revision: alts primero, dentro por SLA; la no-alt va al final.
        Usuario otro = usuario(TipoUsuario.ADULTO);
        Denuncia altCercana = denuncia(otro, tutor, true, Instant.now().plusSeconds(3 * 86400));
        Denuncia altLejana = denuncia(otro, tutor, true, Instant.now().plusSeconds(7 * 86400));
        Denuncia normal = denuncia(otro, tutor, false, Instant.now().plusSeconds(5 * 86400));
        Denuncia sinDescargo = denuncia(otro, tutor, true, null);
        sinDescargo.setEstado(EstadoDenuncia.REGISTRADA);
        denunciaRepository.save(sinDescargo);

        // Alertas pendiente_revision: la más vieja es la más urgente (ventana 12hs).
        AlertaSeguridad aVieja = alerta(tutor, Instant.now().minusSeconds(3 * 3600));
        AlertaSeguridad aMedia = alerta(tutor, Instant.now().minusSeconds(2 * 3600));
        AlertaSeguridad aNueva = alerta(tutor, Instant.now().minusSeconds(3600));

        String dJson = mvc.perform(get("/api/admin/moderacion/denuncias")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(idsDe(dJson)).containsExactly(altCercana.getId(), altLejana.getId(), normal.getId());

        String aJson = mvc.perform(get("/api/admin/moderacion/alertas")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(idsDe(aJson)).containsExactly(aVieja.getId(), aMedia.getId(), aNueva.getId());
    }

    private Denuncia denuncia(Usuario denunciante, Usuario denunciado, boolean prioridadAlta,
                              Instant slaResolucionVenceAt) {
        Denuncia d = new Denuncia();
        d.setDenuncianteId(denunciante.getId());
        d.setDenunciadoId(denunciado.getId());
        d.setMotivo(MotivoDenuncia.FRAUDE);
        d.setEstado(EstadoDenuncia.EN_REVISION);
        d.setDescargoRecibidoAt(Instant.now());
        d.setPrioridadAlta(prioridadAlta);
        d.setSlaResolucionVenceAt(slaResolucionVenceAt);
        return denunciaRepository.save(d);
    }

    private AlertaSeguridad alerta(Usuario detectado, Instant createdAt) {
        AlertaSeguridad a = new AlertaSeguridad();
        a.setSesionId(sesionConReserva());
        a.setRama("menor");
        a.setDetectadoId(detectado.getId());
        a.setCreatedAt(createdAt);
        return alertaRepository.save(a);
    }

    @Test
    void tickets_enrutadosPorMapeo_orientanACadaRol_sinMapeoSeRechaza() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);

        mvc.perform(post("/api/soporte/tickets")
                        .header("Authorization", "Bearer " + token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "origenModulo", "M1.credencial_agotada",
                                "asunto", "Mi credencial se venció",
                                "detalle", "Quiero subirla de nuevo."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rolAsignado").value(RolAdmin.MODERACION_SEGURIDAD.getValor()));

        mvc.perform(post("/api/soporte/tickets")
                        .header("Authorization", "Bearer " + token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "origenModulo", "M5.pago_fallido",
                                "asunto", "No me llega la liberación",
                                "detalle", "Mi escrow lleva días retenido."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rolAsignado").value(RolAdmin.SOPORTE_FINANCIERO.getValor()));

        // Origen sin mapeo → 422, fail-closed (no se persiste un ticket huérfano).
        mvc.perform(post("/api/soporte/tickets")
                        .header("Authorization", "Bearer " + token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "origenModulo", "X9.origen_desconocido",
                                "asunto", "Sí",
                                "detalle", "Sí"))))
                .andExpect(status().isUnprocessableEntity());

        // FR-ADM-008: la cola de cada Admin solo ve sus tickets.
        String colaSoporte = mvc.perform(get("/api/admin/tickets")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(idsDe(colaSoporte)).hasSize(1);
        assertThat(objectMapper.readTree(colaSoporte).get(0).get("rolAsignado").asText())
                .isEqualTo(RolAdmin.SOPORTE_FINANCIERO.getValor());

        String colaModeracion = mvc.perform(get("/api/admin/tickets")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(idsDe(colaModeracion)).hasSize(1);
        assertThat(objectMapper.readTree(colaModeracion).get(0).get("rolAsignado").asText())
                .isEqualTo(RolAdmin.MODERACION_SEGURIDAD.getValor());
    }

    @Test
    void tickets_actualizarEstado_soloElRolAsignado_yFijaResueltoEnUnaVezSola() throws Exception {
        // Auditoría 2026-09-18 (gap del frontend): antes no existía ningún
        // endpoint de escritura de estado — el ticket nacía y quedaba ahí.
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);

        String creado = mvc.perform(post("/api/soporte/tickets")
                        .header("Authorization", "Bearer " + token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "origenModulo", "M1.credencial_agotada",
                                "asunto", "Mi credencial se venció",
                                "detalle", "Quiero subirla de nuevo."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ticketId = objectMapper.readTree(creado).get("id").asText();

        // Rol asignado = Moderación (mapeo de M1.credencial_agotada). Soporte
        // Financiero, aunque sea un Admin válido, no puede tocar este ticket.
        mvc.perform(patch("/api/admin/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "en_proceso"))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "en_proceso"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("en_proceso"))
                .andExpect(jsonPath("$.resueltoEn").doesNotExist());

        String resuelto = mvc.perform(patch("/api/admin/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "resuelto"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("resuelto"))
                .andExpect(jsonPath("$.resueltoEn").exists())
                .andReturn().getResponse().getContentAsString();
        String resueltoEnPrimeraVez = objectMapper.readTree(resuelto).get("resueltoEn").asText();

        // Cerrado después: resueltoEn NO se pisa (se fijó la primera vez).
        String cerrado = mvc.perform(patch("/api/admin/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "cerrado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cerrado"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(cerrado).get("resueltoEn").asText())
                .isEqualTo(resueltoEnPrimeraVez);

        mvc.perform(patch("/api/admin/tickets/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "cerrado"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void pagosFallidos_soloEscrowsAgotados_reintentarReutilizaElFlujoDeM5() throws Exception {
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        Transaccion agotada = transaccion(EstadoTransaccion.RETENIDO_ESCROW, 3);
        transaccion(EstadoTransaccion.RETENIDO_ESCROW, 2);
        transaccion(EstadoTransaccion.LIBERADO, 3);

        String json = mvc.perform(get("/api/admin/financiero/pagos-fallidos")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(idsDe(json)).containsExactly(agotada.getId());

        // El reintento manual ejecuta el flujo REAL de M5 (mock proveedor = éxito).
        mvc.perform(post("/api/admin/financiero/pagos-fallidos/" + agotada.getId() + "/reintentar")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(EstadoTransaccion.LIBERADO.getValor()));
        assertThat(transaccionRepository.findById(agotada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.LIBERADO);

        // Fuera de la cola (reintentos sin agotar) → 422; inexistente → 404.
        Transaccion enRetintos = transaccion(EstadoTransaccion.RETENIDO_ESCROW, 2);
        mvc.perform(post("/api/admin/financiero/pagos-fallidos/" + enRetintos.getId() + "/reintentar")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/financiero/pagos-fallidos/" + UUID.randomUUID() + "/reintentar")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isNotFound());

        // 403 cruzado: moderación no toca Soporte Financiero.
        mvc.perform(get("/api/admin/financiero/pagos-fallidos")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isForbidden());

        // US-6: la acción financiera también quedó auditada (auditoria transversal).
        assertThat(auditoriaDe("GET /api/admin/financiero/pagos-fallidos")).hasSize(1);
        assertThat(auditoriaDe(
                "POST /api/admin/financiero/pagos-fallidos/" + agotada.getId() + "/reintentar"))
                .hasSize(1);
    }

    @Test
    void reembolsoParcial_manualPorDisputa_delegaEnElProveedorYNoTocaElEscrow() throws Exception {
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(usuario(TipoUsuario.TUTOR));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion enEscrow = new Transaccion();
        enEscrow.setReservaId(reserva.getId());
        String mpPaymentId = "mp-parcial-" + CONTADOR.incrementAndGet();
        enEscrow.setMpPaymentId(mpPaymentId);
        enEscrow.setMontoBruto(new BigDecimal("15000.00"));
        enEscrow.setComisionPlataforma(new BigDecimal("2250.00"));
        enEscrow.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
        transaccionRepository.save(enEscrow);

        // FR-PAG-010: parcial por disputa, monto < total → delega en el proveedor
        // (mock real) con el mpPaymentId correspondiente. El escrow sigue igual:
        // el resto lo maneja el flujo normal de liberación (Spec no define estado).
        mvc.perform(post("/api/admin/financiero/transacciones/"
                        + enEscrow.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(EstadoTransaccion.RETENIDO_ESCROW.getValor()))
                .andExpect(jsonPath("$.montoBruto").value(new BigDecimal("15000.0")));
        verify(reembolsoParcial).reembolsarParcial(mpPaymentId, new BigDecimal("6000"));
        assertThat(transaccionRepository.findById(enEscrow.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);

        // Guards: monto = total → 422 (eso es reembolso total, otro flujo),
        // escrow ya liberado → 422, inexistente → 404, 403 cruzado.
        mvc.perform(post("/api/admin/financiero/transacciones/"
                        + enEscrow.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 15000))))
                .andExpect(status().isUnprocessableEntity());

        // V24 (AUD-010): uq_transacciones_reserva exige una Transaccion por
        // Reserva — esta fixture necesita su propia Reserva, no puede reusar la
        // de enEscrow (antes de la migración, dos filas para la misma reserva
        // era precisamente el bug: escrow duplicado irrecuperable).
        Reserva reservaLiberada = new Reserva();
        reservaLiberada.setPagador(pagador);
        reservaLiberada.setBeneficiario(pagador);
        reservaLiberada.setTutor(usuario(TipoUsuario.TUTOR));
        // AUD-009: mismo beneficiario → no puede superponerse con la reserva de arriba.
        reservaLiberada.setHorario(Instant.now().plusSeconds(3 * 3600));
        reservaLiberada.setPrecio(BigDecimal.valueOf(15000));
        reservaLiberada.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reservaLiberada);

        Transaccion liberada = new Transaccion();
        liberada.setReservaId(reservaLiberada.getId());
        liberada.setMpPaymentId("mp-liberada-" + CONTADOR.incrementAndGet());
        liberada.setMontoBruto(new BigDecimal("15000.00"));
        liberada.setComisionPlataforma(new BigDecimal("2250.00"));
        liberada.setEstado(EstadoTransaccion.LIBERADO);
        transaccionRepository.save(liberada);
        mvc.perform(post("/api/admin/financiero/transacciones/"
                        + liberada.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/financiero/transacciones/" + UUID.randomUUID() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/financiero/transacciones/"
                        + enEscrow.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isForbidden());

        assertThat(auditoriaDe(
                "POST /api/admin/financiero/transacciones/" + enEscrow.getId() + "/reembolso-parcial"))
                .hasSize(1);
    }

    @Test
    void reembolsoParcial_sobrePausadoAlerta_422() throws Exception {
        // FASE2-10: un reembolso parcial manual sobre dinero congelado por una
        // Alerta de seguridad adelantaría la decisión de M9 — el estado de la
        // pausa por Alerta no es parcializable (la resuelve el track de seguridad).
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);

        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(usuario(TipoUsuario.TUTOR));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion pausadaPorAlerta = new Transaccion();
        pausadaPorAlerta.setReservaId(reserva.getId());
        pausadaPorAlerta.setMpPaymentId("mp-alerta-" + CONTADOR.incrementAndGet());
        pausadaPorAlerta.setMontoBruto(new BigDecimal("15000.00"));
        pausadaPorAlerta.setComisionPlataforma(new BigDecimal("2250.00"));
        pausadaPorAlerta.setEstado(EstadoTransaccion.PAUSADO_ALERTA);
        transaccionRepository.save(pausadaPorAlerta);

        mvc.perform(post("/api/admin/financiero/transacciones/"
                        + pausadaPorAlerta.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(reembolsoParcial);
    }

    @Test
    void preciosRegionales_nuncaSobrescriben_agreganVersionNuevaPorProvincia() throws Exception {
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        mvc.perform(post("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provincia", "Santa Fe", "valorSugerido", 20000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(post("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provincia", "Santa Fe", "valorSugerido", 25000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        List<PrecioReferenciaRegional> filas = precioRepository.findAll().stream()
                .filter(p -> p.getProvincia().equals("Santa Fe")).toList();
        assertThat(filas).hasSize(2);
        assertThat(precioRepository.findFirstByProvinciaOrderByVersionDesc("Santa Fe")
                .orElseThrow().getValorSugerido()).isEqualByComparingTo("25000");

        // 403 de rol cruzado.
        mvc.perform(post("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provincia", "CABA", "valorSugerido", 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void preciosRegionales_listado_devuelveSoloLaVersionVigentePorProvincia() throws Exception {
        // Auditoría 2026-09-18 (gap del frontend): antes solo había POST a
        // ciegas, sin forma de ver la tabla vigente antes de tocarla.
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        mvc.perform(post("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provincia", "Cordoba", "valorSugerido", 18000))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provincia", "Cordoba", "valorSugerido", 21000))))
                .andExpect(status().isOk());

        String json = mvc.perform(get("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<tools.jackson.databind.JsonNode> filas = new java.util.ArrayList<>();
        objectMapper.readTree(json).forEach(filas::add);
        List<tools.jackson.databind.JsonNode> cordoba = filas.stream()
                .filter(f -> f.get("provincia").asText().equals("Cordoba")).toList();
        // Solo la fila VIGENTE (version 2), nunca la version 1 ya superada.
        assertThat(cordoba).hasSize(1);
        assertThat(cordoba.get(0).get("version").asInt()).isEqualTo(2);
        assertThat(cordoba.get(0).get("valorSugerido").asInt()).isEqualTo(21000);

        mvc.perform(get("/api/admin/financiero/precios-regionales")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isForbidden());
    }

    @Test
    void credenciales_resolverAprobarRechazar_404_422_403_auditado() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);

        Usuario tutorA = usuario(TipoUsuario.TUTOR);
        CredencialAcademica aprobar = credencial(tutorA, EstadoCredencial.PENDIENTE, null);
        Usuario tutorB = usuario(TipoUsuario.TUTOR);
        CredencialAcademica rechazar = credencial(tutorB, EstadoCredencial.PENDIENTE, null);
        CredencialAcademica yaResuelta = credencial(tutorA, EstadoCredencial.APROBADO, null);

        // Aprobar: la credencial pasa a APROBADO y habilita el matching del Tutor.
        mvc.perform(post("/api/admin/moderacion/credenciales/" + aprobar.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APROBAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(EstadoCredencial.APROBADO.name()));
        assertThat(usuarioRepository.findById(tutorA.getId()).orElseThrow().isActivoParaMatching())
                .isTrue();

        // Rechazar (intento 1: no dispara backoff).
        mvc.perform(post("/api/admin/moderacion/credenciales/" + rechazar.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "RECHAZAR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(EstadoCredencial.RECHAZADO.name()));

        // Ya resuelta → 422; inexistente → 404; rol cruzado → 403.
        mvc.perform(post("/api/admin/moderacion/credenciales/" + yaResuelta.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APROBAR"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/moderacion/credenciales/" + UUID.randomUUID() + "/resolver")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APROBAR"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/moderacion/credenciales/" + aprobar.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APROBAR"))))
                .andExpect(status().isForbidden());

        // Auditoría transversal de la acción de resolución.
        assertThat(auditoriaDe("POST /api/admin/moderacion/credenciales/" + aprobar.getId() + "/resolver"))
                .hasSize(1);
        assertThat(auditoriaDe("POST /api/admin/moderacion/credenciales/" + rechazar.getId() + "/resolver"))
                .hasSize(1);
    }

    /**
     * Revisión por rol (R1): un Tutor puede ser además Admin, pero nunca resuelve un caso en el
     * que es parte — su credencial, una Denuncia o Alerta que lo involucra, el dinero de una
     * Reserva suya → 403 sin tocar nada. Y una cuenta de Menor nunca opera el panel (Art. II).
     */
    private Usuario tutorAdmin(RolAdmin rol) {
        Usuario u = usuario(TipoUsuario.TUTOR);
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(u);
        fila.setRol(rol);
        adminRepository.save(fila);
        return u;
    }

    @Test
    void r1_adminNoResuelveCasosPropios_yUnMenorNuncaEsAdmin_403() throws Exception {
        Usuario tutorAdmin = tutorAdmin(RolAdmin.MODERACION_SEGURIDAD);
        String tk = token(tutorAdmin);

        CredencialAcademica propia = credencial(tutorAdmin, EstadoCredencial.PENDIENTE, null);
        mvc.perform(post("/api/admin/moderacion/credenciales/" + propia.getId() + "/resolver")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APROBAR"))))
                .andExpect(status().isForbidden());
        assertThat(credencialRepository.findById(propia.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCredencial.PENDIENTE);

        Denuncia contraEl = denuncia(usuario(TipoUsuario.ADULTO), tutorAdmin, false,
                Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/admin/moderacion/denuncias/" + contraEl.getId() + "/resolver")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("resolucion", "infundada"))))
                .andExpect(status().isForbidden());
        assertThat(denunciaRepository.findById(contraEl.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDenuncia.EN_REVISION);

        AlertaSeguridad sobreEl = alerta(tutorAdmin, Instant.now());
        mvc.perform(post("/api/admin/moderacion/alertas-seguridad/" + sobreEl.getId() + "/resolver")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "reactivar"))))
                .andExpect(status().isForbidden());

        Usuario tutorSoporte = tutorAdmin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Reserva suya = new Reserva();
        suya.setPagador(pagador);
        suya.setBeneficiario(pagador);
        suya.setTutor(tutorSoporte);
        suya.setHorario(Instant.now().plusSeconds(3600));
        suya.setPrecio(BigDecimal.valueOf(15000));
        suya.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(suya);
        Transaccion t = new Transaccion();
        t.setReservaId(suya.getId());
        t.setMpPaymentId("mp-propia-" + CONTADOR.incrementAndGet());
        t.setMontoBruto(new BigDecimal("15000.00"));
        t.setComisionPlataforma(new BigDecimal("2250.00"));
        t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
        transaccionRepository.save(t);
        mvc.perform(post("/api/admin/financiero/transacciones/" + t.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(tutorSoporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reembolsoParcial);

        // Art. II: aunque alguien le cargue una fila en admins, un Menor no entra al panel.
        Usuario menor = new Usuario();
        menor.setDni(String.format("%08d", 41_000_000 + CONTADOR.incrementAndGet()));
        menor.setNombre("Menor");
        menor.setApellido("Lopez");
        menor.setFechaNacimiento(LocalDate.of(2014, 5, 15));
        menor.setTipo(TipoUsuario.MENOR);
        menor.setPasswordHash("hash");
        menor.setAdultoResponsable(usuario(TipoUsuario.ADULTO));
        menor = usuarioRepository.save(menor);
        com.tinku.admin.model.Admin filaMenor = new com.tinku.admin.model.Admin();
        filaMenor.setUsuario(menor);
        filaMenor.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(filaMenor);
        mvc.perform(get("/api/admin/moderacion/denuncias")
                        .header("Authorization", "Bearer " + token(menor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInactivo_noAutoriza_403() throws Exception {
        Usuario moderador = usuario(TipoUsuario.ADULTO);
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(moderador);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        fila.setActivo(false);
        adminRepository.save(fila);

        mvc.perform(get("/api/admin/moderacion/denuncias")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code GET /api/admin/yo}: el rol del Admin logueado, para que el menú del
     * panel oculte lo que ese rol no puede usar (B10). Gateado como el resto de
     * /api/admin/** (admin activo de CUALQUIER rol); un usuario común → 403.
     */
    @Test
    void adminYo_exponeElRol_gateadoComoElRestoDeAdmin() throws Exception {
        mvc.perform(get("/api/admin/yo")
                        .header("Authorization", "Bearer " + token(admin(RolAdmin.SOPORTE_FINANCIERO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("soporte_financiero"));

        mvc.perform(get("/api/admin/yo")
                        .header("Authorization", "Bearer " + token(admin(RolAdmin.MODERACION_SEGURIDAD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("moderacion_seguridad"));

        mvc.perform(get("/api/admin/yo")
                        .header("Authorization", "Bearer " + token(usuario(TipoUsuario.ADULTO))))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- R4: reembolso del adicional

    @Autowired com.tinku.pagos.service.ReembolsoAdicionalOutbox reembolsoAdicional;
    @Autowired org.quartz.Scheduler scheduler;

    private Transaccion conAdicional(boolean bypass) {
        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(usuario(TipoUsuario.TUTOR));
        reserva.setHorario(Instant.now().minusSeconds(7200));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepository.save(reserva);
        Transaccion t = new Transaccion();
        t.setReservaId(reserva.getId());
        t.setMpPaymentId("mp-adic-" + CONTADOR.incrementAndGet());
        t.setMontoBruto(new BigDecimal("15770.00"));
        t.setComisionPlataforma(new BigDecimal("4050.00"));
        t.setMontoAdicionalResumen(new BigDecimal("770.00"));
        t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
        t.setEnBypass(bypass);
        return transaccionRepository.save(t);
    }

    /** Corre el job a mano (sacando el trigger real para que Quartz no compita con el test). */
    private java.util.Date correrJobAdicional(UUID transaccionId) throws Exception {
        org.quartz.TriggerKey key = com.tinku.pagos.service.ReembolsoAdicionalOutbox.trigger(transaccionId);
        scheduler.unscheduleJob(key);
        reembolsoAdicional.ejecutar(transaccionId);
        org.quartz.Trigger siguiente = scheduler.getTrigger(key);
        return siguiente == null ? null : siguiente.getStartTime();
    }

    private Transaccion recargar(Transaccion t) {
        return transaccionRepository.findById(t.getId()).orElseThrow();
    }

    /** Regresión: un fallo de MP ya no queda solo en un log; reintenta 5/15/60 y cae en la cola. */
    @Test
    void r4_adicionalFallaMp_reintentaConBackoff_yQuedaFallidoEnLaCola() throws Exception {
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Transaccion t = conAdicional(false);
        org.mockito.Mockito.doThrow(new com.tinku.pagos.service.MercadoPagoNoDisponibleException())
                .when(reembolsoParcial).reembolsarParcial(org.mockito.ArgumentMatchers.eq(t.getMpPaymentId()),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        reembolsoAdicional.reembolsarAdicional(t.getReservaId());
        reembolsoAdicional.reembolsarAdicional(t.getReservaId()); // idempotente
        assertThat(recargar(t).getAdicionalReembolsoEstado())
                .isEqualTo(com.tinku.pagos.model.EstadoReembolsoAdicional.PENDIENTE);

        long[] minutos = {5, 15, 60};
        for (long m : minutos) {
            java.util.Date proximo = correrJobAdicional(t.getId());
            assertThat(proximo).isNotNull();
            assertThat(java.time.Duration.between(Instant.now(), proximo.toInstant()).toMinutes())
                    .isBetween(m - 1, m);
        }
        assertThat(correrJobAdicional(t.getId())).isNull();
        Transaccion fallida = recargar(t);
        assertThat(fallida.getAdicionalReembolsoEstado()).isEqualTo(com.tinku.pagos.model.EstadoReembolsoAdicional.FALLIDO);
        assertThat(fallida.getAdicionalReembolsoIntentos()).isEqualTo(4);
        assertThat(fallida.getAdicionalReembolsadoAt()).isNull();
        verify(reembolsoParcial, org.mockito.Mockito.times(4)).reembolsarParcial(t.getMpPaymentId(),
                new BigDecimal("770.00"), "adicional-" + t.getId());

        mvc.perform(get("/api/admin/financiero/reembolsos-adicional")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + t.getId() + "')].intentos").value(4));
    }

    @Test
    void r4_soporteReintentaOResuelveAMano_soloSobreFallidos() throws Exception {
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Transaccion t = conAdicional(false);
        t.setAdicionalReembolsoEstado(com.tinku.pagos.model.EstadoReembolsoAdicional.FALLIDO);
        t.setAdicionalReembolsoIntentos(4);
        transaccionRepository.save(t);

        mvc.perform(post("/api/admin/financiero/reembolsos-adicional/" + t.getId() + "/reintentar")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/financiero/reembolsos-adicional/" + t.getId() + "/reintentar")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        assertThat(correrJobAdicional(t.getId())).isNull();
        assertThat(recargar(t).getAdicionalReembolsoEstado()).isEqualTo(com.tinku.pagos.model.EstadoReembolsoAdicional.HECHO);
        assertThat(recargar(t).getAdicionalReembolsadoAt()).isNotNull();
        // Ya no está fallido: no se reintenta de nuevo.
        mvc.perform(post("/api/admin/financiero/reembolsos-adicional/" + t.getId() + "/reintentar")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isUnprocessableEntity());

        Transaccion otra = conAdicional(false);
        otra.setAdicionalReembolsoEstado(com.tinku.pagos.model.EstadoReembolsoAdicional.FALLIDO);
        transaccionRepository.save(otra);
        mvc.perform(post("/api/admin/financiero/reembolsos-adicional/" + otra.getId() + "/resuelto-manual")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"Devuelto desde el panel de MP, operación 123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTO_MANUAL"));
    }

    @Test
    void r4_bypass_soloMarca_sinLlamarAMercadoPago() {
        Transaccion t = conAdicional(true);
        reembolsoAdicional.reembolsarAdicional(t.getReservaId());
        assertThat(recargar(t).getAdicionalReembolsoEstado()).isEqualTo(com.tinku.pagos.model.EstadoReembolsoAdicional.HECHO);
        verifyNoInteractions(reembolsoParcial);
    }

    // ---------------------------------------------------------------- R5: Mis cobros

    private Transaccion cobroDe(Usuario tutor, EstadoTransaccion estado, String adicional) {
        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(3600L * CONTADOR.incrementAndGet()));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepository.save(reserva);
        Transaccion t = new Transaccion();
        t.setReservaId(reserva.getId());
        t.setMpPaymentId("mp-cobro-" + CONTADOR.incrementAndGet());
        t.setMontoAdicionalResumen(new BigDecimal(adicional));
        t.setMontoBruto(new BigDecimal("15000.00").add(new BigDecimal(adicional)));
        t.setComisionPlataforma(new BigDecimal("4050.00"));
        t.setEstado(estado);
        return transaccionRepository.save(t);
    }

    /** El neto excluye comisión y adicional; la pausa no dice el motivo; solo lo propio; solo Tutor. */
    @Test
    void r5_misCobros_netoSinAdicional_pausaComoEnRevision_soloLoPropio() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        cobroDe(tutor, EstadoTransaccion.RETENIDO_ESCROW, "770.00");
        cobroDe(tutor, EstadoTransaccion.LIBERADO, "0");
        cobroDe(tutor, EstadoTransaccion.PAUSADO_DENUNCIA, "0");
        cobroDe(usuario(TipoUsuario.TUTOR), EstadoTransaccion.LIBERADO, "0");

        mvc.perform(get("/api/pagos/mis-cobros").header("Authorization", "Bearer " + token(tutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobros.length()").value(3))
                .andExpect(jsonPath("$.retenido").value(10950.0))
                .andExpect(jsonPath("$.liberado").value(10950.0))
                .andExpect(jsonPath("$.enRevision").value(10950.0))
                .andExpect(jsonPath("$.cobros[?(@.estado == 'retenido')].precioSesion").value(15000.0))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("denuncia"))));

        mvc.perform(get("/api/pagos/mis-cobros").header("Authorization", "Bearer " + token(usuario(TipoUsuario.ADULTO))))
                .andExpect(status().isForbidden());
    }
}
