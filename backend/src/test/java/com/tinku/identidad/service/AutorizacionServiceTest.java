package com.tinku.identidad.service;

import com.tinku.identidad.model.AutorizacionTutor;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) de {@link AutorizacionService} —
 * FR-ID-009, T-M1-11. Solo la capacidad "Adulto Responsable" autoriza o marca
 * no confiable, y solo sobre menores a su cargo (Artículo II).
 */
class AutorizacionServiceTest {

    private AutorizacionTutorRepository autorizacionRepo;
    private UsuarioRepository usuarioRepo;
    private AutorizacionService service;

    @BeforeEach
    void setUp() {
        autorizacionRepo = mock(AutorizacionTutorRepository.class);
        usuarioRepo = mock(UsuarioRepository.class);
        service = new AutorizacionService(autorizacionRepo, usuarioRepo);
    }

    private Usuario adultoResponsable() {
        Usuario a = new Usuario();
        a.setId(UUID.randomUUID());
        a.setTipo(TipoUsuario.ADULTO);
        a.setCapacidadAdultoResponsable(true);
        return a;
    }

    private Usuario menor(Usuario responsable) {
        Usuario m = new Usuario();
        m.setId(UUID.randomUUID());
        m.setTipo(TipoUsuario.MENOR);
        m.setAdultoResponsable(responsable);
        return m;
    }

    private Usuario tutor() {
        Usuario t = new Usuario();
        t.setId(UUID.randomUUID());
        t.setTipo(TipoUsuario.TUTOR);
        return t;
    }

    @Test
    void autorizarTutorExitoso() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        Usuario tutor = tutor();
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(usuarioRepo.findById(tutor.getId())).thenReturn(Optional.of(tutor));
        when(autorizacionRepo.findByAdultoResponsableIdAndMenorIdAndTutorId(
                ar.getId(), menor.getId(), tutor.getId())).thenReturn(Optional.empty());
        when(autorizacionRepo.save(any(AutorizacionTutor.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AutorizacionTutor r = service.autorizarTutor(ar, menor.getId(), tutor.getId());

        assertEquals(tutor.getId(), r.getTutor().getId());
        assertFalse(r.isNoConfiable());
    }

    @Test
    void autorizarIdempotenteDevuelveExistente() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        Usuario tutor = tutor();
        AutorizacionTutor existente = new AutorizacionTutor();

        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(usuarioRepo.findById(tutor.getId())).thenReturn(Optional.of(tutor));
        when(autorizacionRepo.findByAdultoResponsableIdAndMenorIdAndTutorId(
                ar.getId(), menor.getId(), tutor.getId())).thenReturn(Optional.of(existente));

        service.autorizarTutor(ar, menor.getId(), tutor.getId());

        verify(autorizacionRepo, never()).save(any());
    }

    @Test
    void menorNoACargoRechaza() {
        Usuario ar = adultoResponsable();
        Usuario otroAdulto = adultoResponsable();
        Usuario menor = menor(otroAdulto); // no es del AR
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));

        assertThrows(MenorNoPerteneceException.class,
                () -> service.autorizarTutor(ar, menor.getId(), UUID.randomUUID()));
    }

    @Test
    void adultoSinCapacidadRechaza() {
        Usuario ar = adultoResponsable();
        ar.setCapacidadAdultoResponsable(false);

        assertThrows(TutorNoAutorizadoException.class,
                () -> service.autorizarTutor(ar, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void menorNoPuedeAutorizarTutor() {
        Usuario menor = new Usuario();
        menor.setId(UUID.randomUUID());
        menor.setTipo(TipoUsuario.MENOR);

        assertThrows(TutorNoAutorizadoException.class,
                () -> service.autorizarTutor(menor, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void marcarNoConfiableExitoso() {
        Usuario ar = adultoResponsable();
        Usuario tutor = tutor();
        when(autorizacionRepo.existsByAdultoResponsableIdAndTutorId(ar.getId(), tutor.getId()))
                .thenReturn(true);

        service.marcarNoConfiable(ar, tutor.getId(), true);

        verify(autorizacionRepo).setNoConfiableParaTutor(ar.getId(), tutor.getId(), true);
    }

    @Test
    void marcarNoConfiableSinAutorizacionPreviaRechaza() {
        Usuario ar = adultoResponsable();
        Usuario tutor = tutor();
        when(autorizacionRepo.existsByAdultoResponsableIdAndTutorId(ar.getId(), tutor.getId()))
                .thenReturn(false);

        assertThrows(TutorNoAutorizadoException.class,
                () -> service.marcarNoConfiable(ar, tutor.getId(), true));
    }
}
