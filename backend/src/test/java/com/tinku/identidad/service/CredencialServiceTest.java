package com.tinku.identidad.service;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) de {@link CredencialService} — US-4,
 * T-M1-10. Cubre el flujo de carga, la transición de Admin (M8) y el disparo
 * del backoff escalado (FR-ID-008/012).
 */
class CredencialServiceTest {

    private CredencialAcademicaRepository credencialRepo;
    private CredencialBackoffService backoffService;
    private UsuarioRepository usuarioRepo;
    private CredencialService service;

    @BeforeEach
    void setUp() {
        credencialRepo = mock(CredencialAcademicaRepository.class);
        backoffService = mock(CredencialBackoffService.class);
        usuarioRepo = mock(UsuarioRepository.class);
        service = new CredencialService(credencialRepo, backoffService, usuarioRepo);
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

    private CredencialAcademica credencial(Usuario tutor, int numeroIntento, EstadoCredencial estado) {
        CredencialAcademica c = new CredencialAcademica();
        c.setId(UUID.randomUUID());
        c.setTutor(tutor);
        c.setTipoDocumento(TipoCredencial.TITULO);
        c.setNumeroIntento(numeroIntento);
        c.setEstado(estado);
        return c;
    }

    @Test
    void cargarCredencialExitoso() {
        Usuario tutor = tutor();
        when(credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE))
                .thenReturn(Optional.empty());
        when(credencialRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutor.getId()))
                .thenReturn(Optional.empty());
        when(credencialRepo.save(any(CredencialAcademica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CredencialAcademica c = service.cargarCredencial(tutor, TipoCredencial.TITULO, "stub:/x");

        assertEquals(EstadoCredencial.PENDIENTE, c.getEstado());
        assertEquals(1, c.getNumeroIntento());
        verify(backoffService).chequearPuedeIntentar(tutor.getId());
    }

    @Test
    void cargarCredencialNoTutorRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cargarCredencial(adulto(), TipoCredencial.TITULO, "stub:/x"));
    }

    @Test
    void cargarConPendienteExistenteRechaza() {
        Usuario tutor = tutor();
        when(credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE))
                .thenReturn(Optional.of(credencial(tutor, 1, EstadoCredencial.PENDIENTE)));

        assertThrows(YaExisteCredencialPendienteException.class,
                () -> service.cargarCredencial(tutor, TipoCredencial.TITULO, "stub:/x"));
    }

    @Test
    void cargarEnBackoffRechaza() {
        Usuario tutor = tutor();
        when(credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new CredencialEnBackoffException(java.time.Duration.ofHours(24)))
                .when(backoffService).chequearPuedeIntentar(tutor.getId());

        assertThrows(CredencialEnBackoffException.class,
                () -> service.cargarCredencial(tutor, TipoCredencial.TITULO, "stub:/x"));
    }

    @Test
    void reintentoTrasRechazoIncrementaIntento() {
        Usuario tutor = tutor();
        CredencialAcademica rechazada = credencial(tutor, 1, EstadoCredencial.RECHAZADO);
        when(credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE))
                .thenReturn(Optional.empty());
        when(credencialRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutor.getId()))
                .thenReturn(Optional.of(rechazada));
        when(credencialRepo.save(any(CredencialAcademica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CredencialAcademica c = service.cargarCredencial(tutor, TipoCredencial.TITULO, "stub:/x");

        assertEquals(2, c.getNumeroIntento()); // FR-ID-008
    }

    @Test
    void marcarAprobada() {
        Usuario tutor = tutor();
        CredencialAcademica pendiente = credencial(tutor, 1, EstadoCredencial.PENDIENTE);
        when(credencialRepo.findById(pendiente.getId())).thenReturn(Optional.of(pendiente));
        when(credencialRepo.save(any(CredencialAcademica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CredencialAcademica r = service.marcarAprobada(pendiente.getId(), UUID.randomUUID());

        assertEquals(EstadoCredencial.APROBADO, r.getEstado());
        assertTrue(tutor.isActivoParaMatching()); // credencial aprobada -> matching habilitado
        verify(usuarioRepo).save(tutor);
        verify(backoffService, never()).registrarCicloAgotado(any());
    }

    @Test
    void marcarRechazadaTercerIntentoDisparaBackoff() {
        Usuario tutor = tutor();
        CredencialAcademica intento3 = credencial(tutor, 3, EstadoCredencial.PENDIENTE);
        when(credencialRepo.findById(intento3.getId())).thenReturn(Optional.of(intento3));
        when(credencialRepo.save(any(CredencialAcademica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.marcarRechazada(intento3.getId(), UUID.randomUUID());

        verify(backoffService).registrarCicloAgotado(tutor.getId()); // FR-ID-012
    }

    @Test
    void marcarRechazadaPrimerIntentoNoDisparaBackoff() {
        Usuario tutor = tutor();
        CredencialAcademica intento1 = credencial(tutor, 1, EstadoCredencial.PENDIENTE);
        when(credencialRepo.findById(intento1.getId())).thenReturn(Optional.of(intento1));
        when(credencialRepo.save(any(CredencialAcademica.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.marcarRechazada(intento1.getId(), UUID.randomUUID());

        verify(backoffService, never()).registrarCicloAgotado(any());
    }
}
