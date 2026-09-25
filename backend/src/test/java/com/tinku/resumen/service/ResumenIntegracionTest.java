package com.tinku.resumen.service;

import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.aula.evento.SesionFinalizadaEvent;
import com.tinku.aula.evento.SesionInterrumpidaEvent;
import com.tinku.resumen.model.ResumenSesion;
import com.tinku.resumen.port.ResumenProveedor;
import com.tinku.resumen.port.ResumenProveedorNoConfiguradoException;
import com.tinku.resumen.port.TranscriptSesionProveedor;
import com.tinku.resumen.repository.ResumenSesionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.MotivoDenuncia;
import com.tinku.seguridad.repository.DenunciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M6 — Resumen Automatico de la Sesion, de punta a punta (T-M6-01..T-M6-08).
 *
 * <p>El proveedor de LLM va mockeado (el archivo del provider real no existe:
 * ADR pendiente, T-FIN-03) y el transcript va mockeado (M3-E almacenara el
 * egress real en V? — hoy {@code TranscriptSesionProveedorNoDisponible}
 * devuelve null). El job REAL de Quartz (JOB_STORE) se verifica en los
 * reintentos y el recordatorio. Solo entran al sistema transcripts con datos
 * personales; se verifica que NADA personal llegue al proveedor (T-M6-08).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ResumenIntegracionTest {

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

    @Autowired ResumenService resumenService;
    @Autowired ResumenSesionRepository resumenRepo;
    @Autowired SesionAprendizajeRepository sesionRepo;
    @Autowired ReservaRepository reservaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired ApplicationEventPublisher events;
    @Autowired Scheduler scheduler;

    @MockBean TranscriptSesionProveedor transcript;
    @MockBean ResumenProveedor proveedor;

    @Value("${tinku.resumen.llm.proveedor:}") String llmProveedor;
    @Value("${tinku.resumen.llm.api-key:}") String llmApiKey;

    /** Transcript realista con datos personales de prueba. */
    private static final String TRANSCRIPT_CON_DATOS =
            "Hola Pablo, soy la profe María. Mi celular es 011 15 5555-1234 y mi email es "
                    + "pablo.perez@gmail.com. Mirá la web https://tinku.ar/repaso. Para la próxima "
                    + "clase me pasás el pago por alias pablo.estudiante.mp o por CBU "
                    + "2850590940090418135201.";

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private record Escena(UUID reservaId, UUID sesionId) {
    }

    @BeforeEach
    void mocksBase() {
        when(transcript.transcript(any(UUID.class))).thenReturn(TRANSCRIPT_CON_DATOS);
        when(proveedor.generarResumen(any())).thenReturn(
                new ResumenProveedor.ResumenResultado("Resumen de prueba de la sesión."));
    }

    // ------------------------------------------------ T-M6-02 (listener + umbral)

    @Test
    void sesionFinalizadaDe10MinCreaFilaPendienteYAgendaGeneracion() throws Exception {
        Escena e = escena(ResumenService.DURACION_MINIMA_SEGUNDOS);

        events.publishEvent(new SesionFinalizadaEvent(
                "M3", e.reservaId(), Instant.now()));

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_PENDIENTE);
        assertThat(fila.isSuspendidoSeguridad()).isFalse();
        // La generación no arranca en el listener: se agenda a +30s en el JOB_STORE
        // (transacción aparte, post-commit de la fila) y el proveedor no se toca todavía.
        assertThat(proximoDisparo(e.sesionId())).isPresent();
        verify(transcript, never()).transcript(any(UUID.class));
        verify(proveedor, never()).generarResumen(any());
    }

    @Test
    void sesionMenorA10MinNoCreaResumen() {
        Escena e = escena(ResumenService.DURACION_MINIMA_SEGUNDOS - 1);

        events.publishEvent(new SesionFinalizadaEvent(
                "M3", e.reservaId(), Instant.now()));

        assertThat(resumenRepo.findBySesionId(e.sesionId())).isNotPresent();
        verify(proveedor, never()).generarResumen(any());
    }

    @Test
    void sesionInterrumpidaNuncaGeneraResumen() {
        // M3 cierra una interrupción con `sesion.interrumpida` (nunca `finalizada`);
        // M6 solo escucha `sesion.finalizada` → la duración >=10min no alcanza.
        Escena e = escena(1200);

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        assertThat(resumenRepo.findBySesionId(e.sesionId())).isNotPresent();
        verify(proveedor, never()).generarResumen(any());
    }

    // ------------------------------------------------ T-M6-03 (seguridad del menor)

    @Test
    void denunciaActivaSuspendeYElProveedorJamasSeLlama() throws Exception {
        Escena e = escena(1200);
        guardarDenuncia(e.sesionId(), EstadoDenuncia.REGISTRADA);

        events.publishEvent(new SesionFinalizadaEvent(
                "M3", e.reservaId(), Instant.now()));

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_SUSPENDIDO_SEGURIDAD);
        assertThat(fila.isSuspendidoSeguridad()).isTrue();
        assertThat(triggerReintentoExiste(e.sesionId())).isFalse();
        // Además, disparar la generación sobre la fila suspendida sigue siendo no-op.
        resumenService.ejecutarGenerar(e.sesionId());
        verify(transcript, never()).transcript(any(UUID.class));
        verify(proveedor, never()).generarResumen(any());
    }

    @Test
    void alertaDeSeguridadPendienteSuspendeYElProveedorJamasSeLlama() {
        Escena e = escena(1200);
        guardarAlerta(e.sesionId());

        events.publishEvent(new SesionFinalizadaEvent(
                "M3", e.reservaId(), Instant.now()));

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_SUSPENDIDO_SEGURIDAD);
        verify(proveedor, never()).generarResumen(any());
    }

    // ------------------------------------------ T-M6-04/05/08 (pipeline + fail-closed)

    @Test
    void generacionExitosaPersisteAnonimizadoYAgendaRecordatorio24Hs() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());

        resumenService.ejecutarGenerar(e.sesionId());

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_GENERADO);
        assertThat(fila.getResumenFinal()).isEqualTo("Resumen de prueba de la sesión.");
        // T-M6-08: el request que recibió el proveedor lleva el transcript YA limpio.
        ArgumentCaptor<ResumenProveedor.ResumenRequest> captor =
                ArgumentCaptor.forClass(ResumenProveedor.ResumenRequest.class);
        verify(proveedor).generarResumen(captor.capture());
        String enviado = captor.getValue().transcriptAnonimizado();
        assertThat(enviado).doesNotContain("Pablo", "María", "gmail", "5555", "tinku.ar",
                "estudiante.mp", "2850590940090418135201");
        assertThat(enviado).contains("[nombre]", "[telefono]", "[email]", "[url]", "[pago]");
        // La data anonimizada queda persistida (T-M6-04), con o sin proveedor.
        assertThat(fila.getTranscriptAnonimizado()).isEqualTo(enviado);
        assertThat(fila.getPromptAnonimizado()).contains("el tutor", "[pago]")
                .doesNotContain("Pablo", "2850590940090418135201");
        // Listo: sin triggers de reintento, uno de recordatorio a las 24hs.
        assertThat(triggerReintentoExiste(e.sesionId())).isFalse();
        var recordatorio = proximoDisparo(ResumenService.triggerRecordatorio(e.sesionId()));
        assertThat(recordatorio).isPresent();
        assertThat(recordatorio.get()).isBetween(
                Instant.now().plus(Duration.ofHours(23)).plus(Duration.ofMinutes(50)),
                Instant.now().plus(Duration.ofHours(24)).plus(Duration.ofMinutes(10)));
        assertThat(fila.isRecordatorioPendiente()).isTrue();
    }

    @Test
    void sinTranscriptUtilLaFilaQuedaFallidaSinInventarContenido() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        when(transcript.transcript(any(UUID.class))).thenReturn(null);

        resumenService.ejecutarGenerar(e.sesionId());

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_FALLIDO);
        assertThat(fila.getResumenFinal()).isNull();
        verify(proveedor, never()).generarResumen(any());
        assertThat(triggerReintentoExiste(e.sesionId())).isFalse();
    }

    @Test
    void sinProveedorConfiguradoQuedaReintentoAgotadoSinReintentar() throws Exception {
        // Fail-closed T-M6-05: config ausente es determinística, no transitoria —
        // no se reintenta y NUNCA se llama a un provente vacío.
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        org.mockito.Mockito.doThrow(new ResumenProveedorNoConfiguradoException())
                .when(proveedor).generarResumen(any());

        resumenService.ejecutarGenerar(e.sesionId());

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_REINTENTO_AGOTADO);
        assertThat(fila.getIntentos()).isZero();
        assertThat(triggerReintentoExiste(e.sesionId())).isFalse();
        // La anonimización igual se persistió (siempre, T-M6-04).
        assertThat(fila.getTranscriptAnonimizado()).contains("[nombre]");
    }

    // ------------------------------------------------ T-M6-06 (backoff FR-SUM-007)

    @Test
    void falloTransitorioReintentaConBackoffDe5Min() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        org.mockito.Mockito.doThrow(new RuntimeException("LLM caído"))
                .when(proveedor).generarResumen(any());

        resumenService.ejecutarGenerar(e.sesionId());

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_PENDIENTE);
        assertThat(fila.getIntentos()).isEqualTo(1);
        var proximo = proximoDisparo(e.sesionId());
        assertThat(proximo).isPresent();
        assertThat(proximo.get()).isBetween(
                Instant.now().plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(6)));
    }

    @Test
    void falloTransitorioAcumulaBackoffDe15Min() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        org.mockito.Mockito.doThrow(new RuntimeException("LLM caído"))
                .when(proveedor).generarResumen(any());

        resumenService.ejecutarGenerar(e.sesionId());
        resumenService.ejecutarGenerar(e.sesionId());

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getIntentos()).isEqualTo(2);
        var proximo = proximoDisparo(e.sesionId());
        assertThat(proximo).isPresent();
        assertThat(proximo.get()).isBetween(
                Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void trasLos3ReintentosQuedaSinResumenYLoSesionNoFalla() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        org.mockito.Mockito.doThrow(new RuntimeException("LLM caído"))
                .when(proveedor).generarResumen(any());

        for (int i = 0; i < 4; i++) {
            resumenService.ejecutarGenerar(e.sesionId());
        }

        ResumenSesion fila = resumenRepo.findBySesionId(e.sesionId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_REINTENTO_AGOTADO);
        assertThat(fila.getIntentos()).isEqualTo(4);
        assertThat(fila.getResumenFinal()).isNull();
        assertThat(triggerReintentoExiste(e.sesionId())).isFalse();
        // FR-SUM-007: la sesión de aprendizaje queda intacta (no marcada fallida).
        assertThat(sesionRepo.findById(e.sesionId()).orElseThrow().getEstado())
                .isEqualTo(SesionAprendizaje.ESTADO_FINALIZADA);
    }

    // ------------------------------------------------ T-M6-07 (recordatorio único)

    @Test
    void elRecordatorioDelResumenSeEnviaUnaSolaVez() throws Exception {
        Escena e = escena(1200);
        filaPendiente(e.sesionId());
        resumenService.ejecutarGenerar(e.sesionId());
        assertThat(resumenRepo.findBySesionId(e.sesionId()).orElseThrow()
                .isRecordatorioPendiente()).isTrue();

        scheduler.triggerJob(ResumenService.jobRecordatorio(e.sesionId()));
        esperarHasta(() -> !resumenRepo.findBySesionId(e.sesionId()).orElseThrow()
                .isRecordatorioPendiente());

        // Una segunda ejecución (trigger vencido re-disparado) es un no-op.
        scheduler.triggerJob(ResumenService.jobRecordatorio(e.sesionId()));
        assertThat(resumenRepo.findBySesionId(e.sesionId()).orElseThrow()
                .isRecordatorioPendiente()).isFalse();
    }

    // ------------------------------------------------ solo configuración (T-M6-05)

    @Test
    void placeholdersDelProveedorExistenVaciosPorDefecto() {
        // Property test: la clave del LLM vive en application.yml como placeholder
        // (ADR pendiente, T-FIN-03); sin env, resuelve vacío → fail-closed.
        assertThat(llmProveedor).isEmpty();
        assertThat(llmApiKey).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private Escena escena(int duracionSegundos) {
        Usuario adulto = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Reserva reserva = new Reserva();
        reserva.setPagador(adulto);
        reserva.setBeneficiario(adulto);
        reserva.setTutor(guardarUsuario(TipoUsuario.TUTOR, "Sergio"));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        SesionAprendizaje sesion = new SesionAprendizaje();
        sesion.setReservaId(reserva.getId());
        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        sesion.setDuracionAgendadaSegundos(3600);
        sesion.setInicioReal(Instant.now().minusSeconds(duracionSegundos));
        sesion.setFinReal(Instant.now());
        sesion.setDuracionEfectivaSegundos(duracionSegundos);
        sesionRepo.save(sesion);

        return new Escena(reserva.getId(), sesion.getId());
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 42_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(
                tipo == TipoUsuario.TUTOR ? LocalDate.of(1999, 5, 15) : LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    private void guardarDenuncia(UUID sesionId, EstadoDenuncia estado) {
        Denuncia denuncia = new Denuncia();
        denuncia.setDenuncianteId(guardarUsuario(TipoUsuario.ADULTO, "Laura").getId());
        denuncia.setDenunciadoId(guardarUsuario(TipoUsuario.TUTOR, "Diego").getId());
        denuncia.setSesionId(sesionId);
        denuncia.setMotivo(MotivoDenuncia.ACOSO);
        denuncia.setEstado(estado);
        denunciaRepository.save(denuncia);
    }

    private void guardarAlerta(UUID sesionId) {
        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesionId);
        alerta.setRama("menor");
        alerta.setDetectadoId(guardarUsuario(TipoUsuario.TUTOR, "Nicolás").getId());
        alerta.setEstado(AlertaSeguridad.ESTADO_PENDIENTE_REVISION);
        alertaRepository.save(alerta);
    }

    private void filaPendiente(UUID sesionId) {
        ResumenSesion fila = new ResumenSesion();
        fila.setSesionId(sesionId);
        resumenRepo.save(fila);
    }

    private boolean triggerReintentoExiste(UUID sesionId) throws Exception {
        return scheduler.checkExists(ResumenService.triggerReintento(sesionId));
    }

    private Optional<Instant> proximoDisparo(org.quartz.TriggerKey key) throws Exception {
        var trigger = scheduler.getTrigger(key);
        return trigger == null || trigger.getNextFireTime() == null
                ? Optional.empty()
                : Optional.of(trigger.getNextFireTime().toInstant());
    }

    private Optional<Instant> proximoDisparo(UUID sesionId) throws Exception {
        return proximoDisparo(ResumenService.triggerReintento(sesionId));
    }

    private static void esperarHasta(Callable<Boolean> condicion) throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (!condicion.call() && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }
        assertThat(condicion.call()).as("No se cumplió la condición a tiempo").isTrue();
    }
}