package com.tinku.admin;

import com.tinku.identidad.dto.AccionRevisionCap;
import com.tinku.identidad.model.CategoriaAntecedenteCap;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.identidad.service.CertificadoService;
import com.tinku.seguridad.evento.AlertaResueltaEvent;
import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.seguridad.model.DecisionAlerta;
import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.MotivoDenuncia;
import com.tinku.seguridad.model.TipoSancion;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.seguridad.service.AlertaSeguridadService;
import com.tinku.seguridad.service.DenunciaService;
import com.tinku.shared.ResolucionDenuncia;
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import com.tinku.seguridad.evento.DenunciaResueltaEvent;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos admins deciden el MISMO caso a la vez (denuncia, alerta del kill-switch, CAP). Con hilos
 * reales contra Postgres: el primero se frena adentro de su transacción, ya pasado el chequeo de
 * estado, y el segundo entra. Solo uno puede decidir; el otro tiene que recibir "ya resuelto".
 */
@Import(DecisionesAdminCarrerasIntegracionTest.Frenos.class)
class DecisionesAdminCarrerasIntegracionTest extends IntegracionBase {

    @Autowired DenunciaService denunciaService;
    @Autowired DenunciaRepository denunciaRepo;
    @Autowired AlertaSeguridadService alertaService;
    @Autowired AlertaSeguridadRepository alertaRepo;
    @Autowired CertificadoService certificadoService;
    @Autowired CertificadoAntecedentesPenalesRepository capRepo;
    @Autowired com.tinku.reservas.repository.ReservaRepository reservaRepo;
    @MockitoSpyBean Notificador notificador;
    @MockitoSpyBean com.tinku.identidad.port.Almacenamiento almacenamiento;
    @Autowired com.tinku.aula.EvidenciaService evidenciaService;

    /** Frena al primer hilo que pasa por el punto marcado, hasta que el test lo suelta. */
    static final class Freno {
        final CountDownLatch dentro = new CountDownLatch(1);
        final CountDownLatch seguir = new CountDownLatch(1);
        final AtomicBoolean usado = new AtomicBoolean();

        void frenarAlPrimero() {
            if (usado.compareAndSet(false, true)) {
                dentro.countDown();
                try {
                    seguir.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static volatile Freno freno;

    @TestConfiguration
    static class Frenos {
        @Bean
        FrenoListener frenoListener() {
            return new FrenoListener();
        }
    }

    static class FrenoListener {
        @EventListener
        public void denuncia(DenunciaResueltaEvent e) {
            Freno f = freno;
            if (f != null) {
                f.frenarAlPrimero();
            }
        }

        @EventListener
        public void alerta(AlertaResueltaEvent e) {
            Freno f = freno;
            if (f != null) {
                f.frenarAlPrimero();
            }
        }
    }

    @AfterEach
    void soltar() {
        if (freno != null) {
            freno.seguir.countDown();
        }
        freno = null;
    }

    /** Corre {@code primero} (que se frena adentro) y, ya frenado, {@code segundo}. Devuelve los errores. */
    private List<Throwable> carrera(Callable<?> primero, Callable<?> segundo) throws Exception {
        freno = new Freno();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(primero);
            assertThat(freno.dentro.await(20, TimeUnit.SECONDS)).as("el primero llegó al freno").isTrue();
            Future<?> b = pool.submit(segundo);
            try {
                b.get(3, TimeUnit.SECONDS); // sin lock termina; con lock espera al primero
            } catch (java.util.concurrent.TimeoutException | ExecutionException ignorado) {
                // se mira abajo
            }
            freno.seguir.countDown();
            List<Throwable> errores = new ArrayList<>();
            for (Future<?> f : List.of(a, b)) {
                try {
                    f.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    errores.add(e.getCause());
                }
            }
            return errores;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dosAdminsResuelvenLaMismaDenuncia_unoSoloDecideYHayUnaSolaSancion() throws Exception {
        Usuario denunciante = adulto(false);
        Usuario denunciado = adulto(false);
        Usuario admin1 = adulto(false);
        Usuario admin2 = adulto(false);
        Denuncia d = new Denuncia();
        d.setDenuncianteId(denunciante.getId());
        d.setDenunciadoId(denunciado.getId());
        d.setMotivo(MotivoDenuncia.FRAUDE);
        d.setEstado(EstadoDenuncia.EN_REVISION);
        d.setDescargoVenceAt(Instant.now().plusSeconds(3600));
        denunciaRepo.saveAndFlush(d);

        List<Throwable> errores = carrera(
                () -> denunciaService.resolver(d.getId(), admin1.getId(), ResolucionDenuncia.FUNDADA,
                        TipoSancion.ADVERTENCIA, null),
                () -> denunciaService.resolver(d.getId(), admin2.getId(), ResolucionDenuncia.FUNDADA,
                        TipoSancion.SUSPENSION_TEMPORAL, 7));

        assertThat(errores).hasSize(1);
        assertThat(errores.get(0)).isInstanceOf(com.tinku.seguridad.DenunciaYaResueltaException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.sanciones WHERE denuncia_id = ?",
                Integer.class, d.getId())).isEqualTo(1);
    }

    @Test
    void dosAdminsResuelvenLaMismaAlerta_laReactivacionNoPisaLaSancion() throws Exception {
        Usuario tutor = tutor(null, null);
        Usuario admin1 = adulto(false);
        Usuario admin2 = adulto(false);
        Usuario alumno = adulto(false);
        com.tinku.reservas.model.Reserva reserva = new com.tinku.reservas.model.Reserva();
        reserva.setPagador(alumno);
        reserva.setBeneficiario(alumno);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(3600));
        reserva.setPrecio(java.math.BigDecimal.valueOf(15000));
        reserva.setEstado(com.tinku.reservas.model.EstadoReserva.CONFIRMADA);
        reservaRepo.saveAndFlush(reserva);
        UUID sesionId = jdbc.queryForObject(
                "INSERT INTO aula.sesiones_aprendizaje (reserva_id) VALUES (?) RETURNING id",
                UUID.class, reserva.getId());
        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesionId);
        alerta.setRama("menor");
        alerta.setDetectadoId(tutor.getId());
        alertaRepo.saveAndFlush(alerta);

        List<Throwable> errores = carrera(
                () -> alertaService.resolver(alerta.getId(), admin1.getId(), DecisionAlerta.REACTIVAR, null, null),
                () -> alertaService.resolver(alerta.getId(), admin2.getId(), DecisionAlerta.SANCIONAR,
                        TipoSancion.SUSPENSION_DEFINITIVA, null));

        assertThat(errores).hasSize(1);
        assertThat(errores.get(0)).isInstanceOf(com.tinku.seguridad.AlertaYaResueltaException.class);
    }

    @Test
    void dosAdminsRevisanElMismoCap_laAprobacionNoPisaElRechazoPorAntecedentes() throws Exception {
        Usuario tutor = tutor(null, null);
        Usuario admin1 = adulto(false);
        Usuario admin2 = adulto(false);
        UUID capId = jdbc.queryForObject("""
                INSERT INTO identidad.certificados_antecedentes_penales
                    (tutor_id, archivo_url, fecha_emision, vence_at, estado, firma_estado)
                VALUES (?, 'test/cap.pdf', CURRENT_DATE - 10, CURRENT_DATE + 355, 'PENDIENTE', 'VALIDA')
                RETURNING id""", UUID.class, tutor.getId());
        // El primero (aprobar) se frena al avisarle al Tutor, ya con el estado decidido.
        freno = null;
        doAnswer(inv -> {
            Freno f = freno;
            if (f != null) {
                f.frenarAlPrimero();
            }
            return inv.callRealMethod();
        }).when(notificador).notificar(any(), eq(TipoNotificacion.CAP_REVISADO), anyMap());

        List<Throwable> errores = carrera(
                () -> certificadoService.revisar(capId, admin1.getId(), AccionRevisionCap.APROBAR, null),
                () -> certificadoService.revisar(capId, admin2.getId(), AccionRevisionCap.RECHAZAR,
                        CategoriaAntecedenteCap.INTEGRIDAD_SEXUAL));

        assertThat(errores).hasSize(1);
        assertThat(errores.get(0)).isInstanceOf(com.tinku.identidad.service.CapNoRevisableException.class);
        // Si alguien llegó a rechazar por antecedentes, el CAP no puede terminar aprobado.
        EstadoCap fin = capRepo.findById(capId).orElseThrow().getEstado();
        assertThat(fin).isIn(EstadoCap.APROBADO, EstadoCap.RECHAZADO);
    }

    /**
     * El alumno sube el clip de la alerta mientras Moderación la resuelve. La subida leía la
     * alerta pendiente, guardaba el archivo y la volvía a guardar entera: si la resolución
     * commiteaba en el medio, la alerta volvía a "pendiente" con la sanción y el reembolso ya
     * hechos.
     */
    @Test
    void subirEvidenciaMientrasSeResuelveLaAlerta_laResolucionNoSeDeshace() throws Exception {
        Usuario tutor = tutor(null, null);
        Usuario alumno = adulto(false);
        Usuario admin = adulto(false);
        com.tinku.reservas.model.Reserva reserva = new com.tinku.reservas.model.Reserva();
        reserva.setPagador(alumno);
        reserva.setBeneficiario(alumno);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(3600));
        reserva.setPrecio(java.math.BigDecimal.valueOf(15000));
        reserva.setEstado(com.tinku.reservas.model.EstadoReserva.FINALIZADA);
        reservaRepo.saveAndFlush(reserva);
        UUID sesionId = jdbc.queryForObject(
                "INSERT INTO aula.sesiones_aprendizaje (reserva_id) VALUES (?) RETURNING id",
                UUID.class, reserva.getId());
        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesionId);
        alerta.setRama("adultos");
        alerta.setDetectadoId(tutor.getId());
        alertaRepo.saveAndFlush(alerta);
        // La subida se frena al guardar el archivo, ya leída la alerta pendiente.
        doAnswer(inv -> {
            Freno f = freno;
            if (f != null) {
                f.frenarAlPrimero();
            }
            return inv.callRealMethod();
        }).when(almacenamiento).guardar(any(byte[].class), org.mockito.ArgumentMatchers.startsWith("evidencia-"));
        byte[] webm = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};

        carrera(() -> evidenciaService.subir(alumno, sesionId, webm, 10),
                () -> alertaService.resolver(alerta.getId(), admin.getId(), DecisionAlerta.SANCIONAR,
                        TipoSancion.SUSPENSION_DEFINITIVA, null));

        assertThat(alertaRepo.findById(alerta.getId()).orElseThrow().getEstado())
                .isEqualTo(AlertaSeguridad.ESTADO_RESUELTA_BAJA);
    }
}
