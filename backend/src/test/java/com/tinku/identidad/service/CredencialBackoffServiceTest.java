package com.tinku.identidad.service;

import com.tinku.identidad.model.IntentoCredencial;
import com.tinku.identidad.repository.IntentoCredencialRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) del backoff escalado de credencial
 * (FR-ID-012, T-M1-10): 24hs * 2^n → 24→48→96…
 */
class CredencialBackoffServiceTest {

    private final IntentoCredencialRepository repo = mock(IntentoCredencialRepository.class);
    private final CredencialBackoffService svc =
            new CredencialBackoffService(repo, Duration.ofHours(24));
    private final UUID tutorId = UUID.randomUUID();

    @Test
    void primerAgotamientoEspera24Horas() {
        when(repo.findById(tutorId)).thenReturn(Optional.empty());

        Instant hasta = svc.registrarCicloAgotado(tutorId);

        long horas = Duration.between(Instant.now(), hasta).toHours();
        assertTrue(horas >= 23 && horas < 26, "espera ~24hs, fue " + horas);
    }

    @Test
    void escaladaDuplicaCooldown() {
        IntentoCredencial intento = new IntentoCredencial();
        intento.setTutorId(tutorId);
        intento.setVecesCicloAgotado(0);

        // Primer agotamiento (24h) ya registrado: vecesCicloAgotado = 1.
        intento.setVecesCicloAgotado(1);
        when(repo.findById(tutorId)).thenReturn(Optional.of(intento));
        long segundo = Duration.between(Instant.now(), svc.registrarCicloAgotado(tutorId)).toHours();
        assertTrue(segundo >= 47 && segundo < 50, "segundo agotamiento ~48hs, fue " + segundo);

        intento.setVecesCicloAgotado(2);
        when(repo.findById(tutorId)).thenReturn(Optional.of(intento));
        long tercero = Duration.between(Instant.now(), svc.registrarCicloAgotado(tutorId)).toHours();
        assertTrue(tercero >= 95 && tercero < 99, "tercer agotamiento ~96hs, fue " + tercero);
    }

    @Test
    void enEsperaLanzaCredencialEnBackoff() {
        IntentoCredencial intento = new IntentoCredencial();
        intento.setTutorId(tutorId);
        intento.setProximoIntentoPermitido(Instant.now().plus(Duration.ofHours(30)));
        when(repo.findById(tutorId)).thenReturn(Optional.of(intento));

        assertThrows(CredencialEnBackoffException.class,
                () -> svc.chequearPuedeIntentar(tutorId));
    }

    @Test
    void fueraDeEsperaNoLanza() {
        IntentoCredencial intento = new IntentoCredencial();
        intento.setTutorId(tutorId);
        intento.setProximoIntentoPermitido(Instant.now().minus(Duration.ofHours(1)));
        when(repo.findById(tutorId)).thenReturn(Optional.of(intento));

        svc.chequearPuedeIntentar(tutorId); // no debe lanzar
        assertEquals(1, 1);
    }

    @Test
    void sinRegistroPrevionoLanza() {
        when(repo.findById(tutorId)).thenReturn(Optional.empty());
        svc.chequearPuedeIntentar(tutorId); // no debe lanzar
        assertTrue(true);
    }
}
