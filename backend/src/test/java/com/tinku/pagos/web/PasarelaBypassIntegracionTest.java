package com.tinku.pagos.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.admin.repository.LogAuditoriaAdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.ReembolsoParcialProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.PasarelaEstadoRepository;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.pagos.service.LiberacionEscrowService;
import com.tinku.pagos.service.PasarelaService;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.FranjaDisponibilidad;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.repository.FranjaDisponibilidadRepository;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V22 — modo Bypass de la pasarela de punta a punta (HTTP + Security + JPA +
 * Flyway + PostgreSQL via Testcontainers):
 *
 * <ul>
 *   <li>con la pasarela deshabilitada, generarPreferencia NO llama a
 *       MercadoPago, confirma la Reserva igual (misino flujo que el webhook) y
 *       persiste el escrow {@code en_bypass}</li>
 *   <li>liberación y reembolso de transacciones {@code en_bypass} son no-op
 *       locales (jamás se contacta al proveedor con un {@code mp_payment_id}
 *       falso) — incluido el reembolso parcial manual de M8 → 422</li>
 *   <li>el toggle de pasarela (GET/PATCH /api/admin/financiero/pasarela) es
 *       solo de Soporte Financiero, persiste el flag y queda auditado</li>
 *   <li>GET /api/admin/salud expone el estado real de infra y NO llena la
 *       auditoría (excluida a propósito)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PasarelaBypassIntegracionTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired ApplicationEventPublisher events;
    @Autowired PasarelaService pasarelaService;
    @Autowired LiberacionEscrowService liberacionEscrow;

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired FranjaDisponibilidadRepository franjaRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired PasarelaEstadoRepository pasarelaEstadoRepository;
    @Autowired LogAuditoriaAdminRepository auditoriaRepository;

    @MockBean OcrService ocrService;
    @MockBean MatchingServiceClient matchingClient;
    @MockBean ReputacionSignalProvider reputacion;
    @MockBean ReputacionBloqueoProveedor reputacionBloqueo;
    @MockBean MercadoPagoClient mercadopago;
    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean ReembolsoParcialProveedor reembolsoParcial;
    @MockBean AlertaSoporteProveedor alertaSoporte;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    // ---------------------------------------------------------------- helpers

    private void pasarelaOff() {
        pasarelaService.establecerHabilitada(false, null);
    }

    private void pasarelaOn() {
        pasarelaService.establecerHabilitada(true, null);
    }

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

    private Reserva reservaEn(Usuario pagador, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(pagador);
        r.setTutor(usuario(TipoUsuario.TUTOR));
        r.setHorario(Instant.now().plusSeconds(3600));
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(estado);
        return reservaRepository.save(r);
    }

    private com.tinku.pagos.model.Transaccion transaccionBypass(Reserva r) {
        com.tinku.pagos.model.Transaccion t = new com.tinku.pagos.model.Transaccion();
        t.setReservaId(r.getId());
        t.setMpPaymentId("bypass-" + r.getId());
        t.setMontoBruto(BigDecimal.valueOf(15000));
        t.setComisionPlataforma(new BigDecimal("2250.00"));
        t.setEnBypass(true);
        t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
        return t;
    }

    /** Franja puntual del Tutor que cubre el horario de la Reserva — requisito
     * del listener M3 (T-M3-03: sin franja no se agendan los jobs de la Sesión y
     * la confirmación se aborta). 1h: horario ± 30min. */
    private void franjaQueCubre(Reserva r, Usuario tutor) {
        LocalDateTime punto = LocalDateTime.ofInstant(r.getHorario(), ReservasZonaHoraria.ZONA);
        FranjaDisponibilidad f = new FranjaDisponibilidad();
        f.setTutor(tutor);
        f.setFechaEspecifica(punto.toLocalDate());
        f.setHoraInicio(punto.toLocalTime().minusMinutes(30));
        f.setHoraFin(punto.toLocalTime().plusMinutes(30));
        franjaRepository.save(f);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void preferencia_conPasarelaOff_noLlamaAMercadoPago_confirmaYPersisteEscrowSimulado()
            throws Exception {
        pasarelaOff();
        Usuario pagador = usuario(TipoUsuario.ADULTO);
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reservaRepository.save(reserva);
        franjaQueCubre(reserva, tutor);

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + token(pagador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reservaId", reserva.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenciaId").value("bypass-" + reserva.getId()))
                .andExpect(jsonPath("$.bypass").value(true))
                .andExpect(jsonPath("$.initPoint").doesNotExist());

        // El proveedor de pagos NO se tocó en ningún momento.
        verifyNoInteractions(mercadopago);

        // Escrow simulado persistido con el mismo contrato que el real.
        var tx = transaccionRepository.findByReservaId(reserva.getId()).orElseThrow();
        assertThat(tx.isEnBypass()).isTrue();
        assertThat(tx.getMpPaymentId()).isEqualTo("bypass-" + reserva.getId());
        assertThat(tx.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(tx.getMontoBruto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(tx.getComisionPlataforma()).isEqualByComparingTo(new BigDecimal("2250.00"));

        // La Reserva quedó confirmada por el MISMO camino que el webhook.
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);

        pasarelaOn();
    }

    @Test
    void liberacion_enBypass_esEstadoLocal_sinLlamarAlProveedor() {
        pasarelaOff();
        Reserva reserva = reservaEn(usuario(TipoUsuario.ADULTO), EstadoReserva.CONFIRMADA);
        var tx = transaccionRepository.save(transaccionBypass(reserva));

        liberacionEscrow.ejecutarLiberacion(tx.getId());

        assertThat(transaccionRepository.findById(tx.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.LIBERADO);
        verifyNoInteractions(liberacion);
        pasarelaOn();
    }

    @Test
    void reembolsoPorNoShow_enBypass_esEstadoLocal_sinLlamarAlProveedor() {
        pasarelaOff();
        Reserva reserva = reservaEn(usuario(TipoUsuario.ADULTO), EstadoReserva.CONFIRMADA);
        var tx = transaccionRepository.save(transaccionBypass(reserva));

        // sesion.no_show_tutor → EscrowService reembolsa el total (FR-RES-005).
        events.publishEvent(new SesionNoShowTutorEvent(this, reserva.getId()));

        var persistida = transaccionRepository.findById(tx.getId()).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(persistida.getLiberarAt()).isNull();
        verifyNoInteractions(reembolso);
        pasarelaOn();
    }

    @Test
    void reembolsoParcial_enBypass_422_sinTocarAlProveedor() throws Exception {
        pasarelaOff();
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Reserva reserva = reservaEn(usuario(TipoUsuario.ADULTO), EstadoReserva.CONFIRMADA);
        var tx = transaccionRepository.save(transaccionBypass(reserva));

        mockMvc.perform(post("/api/admin/financiero/transacciones/"
                        + tx.getId() + "/reembolso-parcial")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("monto", 6000))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        "Transacción simulada (modo Bypass): no hay dinero real que reembolsar."));
        verifyNoInteractions(reembolsoParcial);
        assertThat(transaccionRepository.findById(tx.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        pasarelaOn();
    }

    @Test
    void pasarela_toggle_soloSoporteFinanciero_auditadoYPersistido() throws Exception {
        pasarelaOff();
        Usuario soporte = admin(RolAdmin.SOPORTE_FINANCIERO);
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        mockMvc.perform(get("/api/admin/financiero/pasarela")
                        .header("Authorization", "Bearer " + token(soporte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(false));

        mockMvc.perform(patch("/api/admin/financiero/pasarela")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("habilitada", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true));

        // Persistido en la fila única y efectivo de inmediato (nunca cacheado).
        assertThat(pasarelaEstadoRepository.findById((short) 1).orElseThrow().isHabilitada()).isTrue();
        assertThat(pasarelaService.estaHabilitada()).isTrue();

        // 403 cruzado y sin body → 400 (@NotNull explícito del DTO).
        mockMvc.perform(get("/api/admin/financiero/pasarela")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/financiero/pasarela")
                        .header("Authorization", "Bearer " + token(moderador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("habilitada", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/financiero/pasarela")
                        .header("Authorization", "Bearer " + token(soporte))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        // US-6: la acción quedó auditada (transversal de M8).
        assertThat(auditoriaRepository.findAll().stream()
                .filter(a -> a.getAccion().equals("PATCH /api/admin/financiero/pasarela"))
                .toList()).hasSize(1);
    }

    @Test
    void salud_infra_muestraEstadoRealDeTest_yNoSpameaAuditoria() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);

        mockMvc.perform(get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTestMode").value(true))
                .andExpect(jsonPath("$.hasSeedData").value(false))
                // En test las credenciales externas están vacías → degradado (fail-closed).
                .andExpect(jsonPath("$.mercadoPago.status").value("degraded"))
                .andExpect(jsonPath("$.liveKit.status").value("degraded"))
                // La DB es el propio Testcontainer: siempre operativa.
                .andExpect(jsonPath("$.database.status").value("operational"))
                .andExpect(jsonPath("$.ocrEngine.status").value("operational"))
                .andExpect(jsonPath("$.database.latencyMs").isNumber())
                .andExpect(jsonPath("$.database.lastChecked").isNotEmpty());

        // 403 a un no-Admin, y el sondeo NO llena la auditoría (excluido).
        mockMvc.perform(get("/api/admin/salud")
                        .header("Authorization", "Bearer " + token(usuario(TipoUsuario.ADULTO))))
                .andExpect(status().isForbidden());
        assertThat(auditoriaRepository.findAll().stream()
                .filter(a -> a.getAccion().equals("GET /api/admin/salud"))
                .toList()).isEmpty();
    }
}