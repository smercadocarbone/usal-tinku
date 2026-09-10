package com.tinku.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.admin.model.LogAuditoriaAdmin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.admin.repository.LogAuditoriaAdminRepository;
import com.tinku.admin.repository.TicketSoporteRepository;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired PrecioReferenciaRegionalRepository precioRepository;
    @Autowired TicketSoporteRepository ticketRepository;
    @Autowired LogAuditoriaAdminRepository auditoriaRepository;

    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean AlertaSoporteProveedor alertaSoporte;

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
        return jwtUtil.generateToken(u.getDni(), u.getTipo().name(), true, true);
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
}