package com.tinku.identidad.service;

import com.tinku.identidad.model.IntentoOcr;
import com.tinku.identidad.repository.IntentoOcrRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) de {@link OcrBackoffService}
 * (T-M1-07, FR-ID-011): contador de 3 intentos por ciclo y espera de 24hs.
 */
class OcrBackoffServiceTest {

    private IntentoOcrRepository repo;
    private OcrBackoffService service;

    @BeforeEach
    void setUp() {
        repo = mock(IntentoOcrRepository.class);
        service = new OcrBackoffService(repo, Duration.ofHours(24));
    }

    @Test
    void sinIntentoPreviosPuedeIntentar() {
        when(repo.findByDni("12345678")).thenReturn(Optional.empty());
        // No debe lanzar.
        service.chequearPuedeIntentar("12345678");
    }

    @Test
    void enPeriodoDeEsperaLanzaBackoff() {
        IntentoOcr intento = new IntentoOcr();
        intento.setProximoIntentoPermitido(Instant.now().plusSeconds(3600));
        when(repo.findByDni("12345678")).thenReturn(Optional.of(intento));

        DocumentoEnBackoffException ex = assertThrows(
                DocumentoEnBackoffException.class,
                () -> service.chequearPuedeIntentar("12345678"));
        // Aprox. 1 hora restante (bank: 55..60 minutos por microsegundos transcurridos).
        assertTrue(ex.getEsperaRestante().toMinutes() >= 55
                && ex.getEsperaRestante().toMinutes() <= 60,
                "espera restante: " + ex.getEsperaRestante().toMinutes() + " min");
    }

    @Test
    void pasadoElPeriodoDeEsperaPuedeIntentar() {
        IntentoOcr intento = new IntentoOcr();
        intento.setProximoIntentoPermitido(Instant.now().minusSeconds(10));
        when(repo.findByDni("12345678")).thenReturn(Optional.of(intento));

        service.chequearPuedeIntentar("12345678"); // no debe lanzar
    }

    @Test
    void primerIntentoFallidoNoActivaEspera() {
        when(repo.findByDni("12345678")).thenReturn(Optional.empty());
        IntentoOcr guardado = new IntentoOcr();
        when(repo.save(any(IntentoOcr.class))).thenAnswer(inv -> inv.getArgument(0));

        int restantes = service.registrarIntentoFallido("12345678");

        assertEquals(2, restantes);
        verify(repo).save(any(IntentoOcr.class));
    }

    @Test
    void alTercerIntentoFallidoActivaEspera24hsYReseteaContador() {
        IntentoOcr intento = new IntentoOcr();
        intento.setDni("12345678");
        intento.setIntentosConsumidos(2);
        intento.setProximoIntentoPermitido(null);
        when(repo.findByDni("12345678")).thenReturn(Optional.of(intento));
        when(repo.save(any(IntentoOcr.class))).thenAnswer(inv -> inv.getArgument(0));

        int restantes = service.registrarIntentoFallido("12345678");

        // El ciclo se agotó: 0 intentos quedan en el ciclo actual (hay que
        // esperar el cooldown). El contador se resetea a 0 para que el
        // PRÓXIMO ciclo vuelva a permitir 3.
        assertEquals(0, restantes);
        verify(repo).save(any(IntentoOcr.class));
        IntentoOcr saved = intento;
        assertEquals(0, saved.getIntentosConsumidos());
        // El cooldown queda ~24hs en el futuro.
        long horas = Duration.between(Instant.now(), saved.getProximoIntentoPermitido()).toHours();
        assertTrue(horas >= 23 && horas <= 24, "cooldown en horas: " + horas);
    }

    @Test
    void segundoIntentoConsecutivoAcumula() {
        IntentoOcr intento = new IntentoOcr();
        intento.setDni("12345678");
        intento.setIntentosConsumidos(1);
        when(repo.findByDni("12345678")).thenReturn(Optional.of(intento));
        when(repo.save(any(IntentoOcr.class))).thenAnswer(inv -> inv.getArgument(0));

        int restantes = service.registrarIntentoFallido("12345678");

        assertEquals(1, restantes);
        assertEquals(2, intento.getIntentosConsumidos());
    }
}
