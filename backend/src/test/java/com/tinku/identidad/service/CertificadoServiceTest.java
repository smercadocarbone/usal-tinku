package com.tinku.identidad.service;

import com.tinku.identidad.dto.AccionRevisionCap;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Docker) de {@link CertificadoService} — US-6,
 * T-M1-15/16/17. Cubre carga (backoff compartido FR-ID-021), revisión
 * (BR-CAP-01/02) y vencimiento a los 12 meses (FR-ID-025).
 */
class CertificadoServiceTest {

    private CertificadoAntecedentesPenalesRepository capRepo;
    private UsuarioRepository usuarioRepo;
    private CredencialBackoffService backoff;
    private CertificadoService service;

    @BeforeEach
    void setUp() {
        capRepo = mock(CertificadoAntecedentesPenalesRepository.class);
        usuarioRepo = mock(UsuarioRepository.class);
        backoff = mock(CredencialBackoffService.class);
        service = new CertificadoService(capRepo, usuarioRepo, backoff);
    }

    private Usuario tutor() {
        Usuario t = new Usuario();
        t.setId(UUID.randomUUID());
        t.setTipo(TipoUsuario.TUTOR);
        return t;
    }

    private Usuario adulto() {
        Usuario a = new Usuario();
        a.setId(UUID.randomUUID());
        a.setTipo(TipoUsuario.ADULTO);
        return a;
    }

    private CertificadoAntecedentesPenales cap(Usuario tutor, int intento, EstadoCap estado) {
        CertificadoAntecedentesPenales c = new CertificadoAntecedentesPenales();
        c.setId(UUID.randomUUID());
        c.setTutor(tutor);
        c.setFechaEmision(LocalDate.now().minusMonths(1));
        c.setVenceAt(LocalDate.now().plusMonths(11));
        c.setNumeroIntento(intento);
        c.setEstado(estado);
        return c;
    }

    // ---- T-M1-15: carga ----

    @Test
    void cargarCapExitoso() {
        Usuario tutor = tutor();
        when(capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE))
                .thenReturn(Optional.empty());
        when(capRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutor.getId()))
                .thenReturn(Optional.empty());
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDate emision = LocalDate.of(2026, 1, 15);
        CertificadoAntecedentesPenales c = service.cargarCap(tutor, "stub:/cap", emision);

        assertEquals(EstadoCap.PENDIENTE, c.getEstado());
        assertEquals(1, c.getNumeroIntento());
        assertEquals(emision.plusMonths(12), c.getVenceAt()); // FR-ID-025
        verify(backoff).chequearPuedeIntentar(tutor.getId());
    }

    @Test
    void cargarCapNoTutorRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cargarCap(adulto(), "stub:/cap", LocalDate.now()));
    }

    @Test
    void cargarCapConPendienteExistenteRechaza() {
        Usuario tutor = tutor();
        when(capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE))
                .thenReturn(Optional.of(cap(tutor, 1, EstadoCap.PENDIENTE)));

        assertThrows(YaExisteCredencialPendienteException.class,
                () -> service.cargarCap(tutor, "stub:/cap", LocalDate.now()));
    }

    @Test
    void cargarCapEnBackoffRechaza() {
        Usuario tutor = tutor();
        when(capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(
                        new CredencialEnBackoffException(java.time.Duration.ofHours(24)))
                .when(backoff).chequearPuedeIntentar(tutor.getId());

        assertThrows(CredencialEnBackoffException.class,
                () -> service.cargarCap(tutor, "stub:/cap", LocalDate.now())); // FR-ID-021 reuse FR-ID-012
    }

    @Test
    void reintentoTrasRechazoIncrementaIntento() {
        Usuario tutor = tutor();
        when(capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE))
                .thenReturn(Optional.empty());
        when(capRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutor.getId()))
                .thenReturn(Optional.of(cap(tutor, 1, EstadoCap.RECHAZADO)));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertEquals(2, service.cargarCap(tutor, "stub:/cap", LocalDate.now()).getNumeroIntento());
    }

    // ---- T-M1-16: revisión ----

    @Test
    void aprobarHabilitaMatching() {
        Usuario tutor = tutor();
        CertificadoAntecedentesPenales c = cap(tutor, 1, EstadoCap.PENDIENTE);
        when(capRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.revisar(c.getId(), UUID.randomUUID(), AccionRevisionCap.APROBAR, null);

        assertEquals(EstadoCap.APROBADO, c.getEstado());
        assertTrue(tutor.isActivoParaMatching()); // habilita matching
        verify(usuarioRepo).save(tutor);
        verify(backoff, never()).registrarCicloAgotado(any());
    }

    @Test
    void rechazarPorBrCap01SinReintentoDisparaBackoffEnTercerIntento() {
        Usuario tutor = tutor();
        CertificadoAntecedentesPenales c = cap(tutor, 3, EstadoCap.PENDIENTE);
        when(capRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.revisar(c.getId(), UUID.randomUUID(), AccionRevisionCap.RECHAZAR, "grooming"); // BR-CAP-01

        assertEquals(EstadoCap.RECHAZADO, c.getEstado());
        verify(backoff).registrarCicloAgotado(tutor.getId()); // FR-ID-021/012
        assertFalse(tutor.isActivoParaMatching());
    }

    @Test
    void enRevisionLegalBrCap02NuncaAutoResuelve() {
        Usuario tutor = tutor();
        CertificadoAntecedentesPenales c = cap(tutor, 1, EstadoCap.PENDIENTE);
        when(capRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.revisar(c.getId(), UUID.randomUUID(), AccionRevisionCap.EN_REVISION_LEGAL, "tenencia"); // BR-CAP-02

        assertEquals(EstadoCap.EN_REVISION_LEGAL, c.getEstado());
        assertTrue(c.isTieneAntecedentes());
        assertEquals("tenencia", c.getCategoriaAntecedente());
        // no se auto-resuelve: queda esperando decisión manual del Admin.
        verify(backoff, never()).registrarCicloAgotado(any());
        assertFalse(tutor.isActivoParaMatching());
    }

    // ---- T-M1-17: vencimiento ----

    @Test
    void marcarVencidosSuspendeMatching() {
        Usuario tutor = tutor();
        tutor.setActivoParaMatching(true);
        CertificadoAntecedentesPenales vencido = cap(tutor, 1, EstadoCap.APROBADO);
        vencido.setVenceAt(LocalDate.now().minusDays(1));

        when(capRepo.findByEstadoInAndVenceAtBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(vencido));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int count = service.marcarVencidos();

        assertEquals(1, count);
        assertEquals(EstadoCap.VENCIDO, vencido.getEstado());
        assertFalse(tutor.isActivoParaMatching()); // FR-ID-025: suspende matching, no la cuenta
        verify(usuarioRepo).save(tutor);
    }

    @Test
    void marcarVencidosNoTocaTutorYaInactivo() {
        Usuario tutor = tutor(); // activoParaMatching ya false
        CertificadoAntecedentesPenales vencido = cap(tutor, 1, EstadoCap.APROBADO);
        vencido.setVenceAt(LocalDate.now().minusDays(1));
        when(capRepo.findByEstadoInAndVenceAtBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(vencido));
        when(capRepo.save(any(CertificadoAntecedentesPenales.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.marcarVencidos();

        verify(usuarioRepo, never()).save(any());
        assertEquals(EstadoCap.VENCIDO, vencido.getEstado());
    }
}
