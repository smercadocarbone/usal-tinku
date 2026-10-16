package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.ConsentimientoMenorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una fecha de nacimiento futura o absurda (año 1800) no es una fecha de un
 * DNI: se rechaza como fecha inválida antes de leer el documento (no consume
 * un intento de foto) y no termina en una cuenta de 226 años ni en un "tenés
 * que ser mayor de 18" engañoso.
 */
class FechaNacimientoInvalidaTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private OcrService ocrService;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        ocrService = mock(OcrService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UsuarioService(usuarioRepo, ocrService, encoder,
                mock(OcrBackoffService.class), mock(ConsentimientoMenorRepository.class));
        // El OCR "lee" lo mismo que se declaró (como el OCR falso de dev/staging).
        when(ocrService.procesarDocumento(any(), any())).thenAnswer(inv -> {
            var d = (com.tinku.identidad.ocr.DatosDniDeclarados) inv.getArgument(1);
            return new ResultadoOcr(true, d.dni(), d.nombre(), d.apellido(), d.fechaNacimiento());
        });
    }

    private RegistroAdultoRequest adulto(LocalDate nacimiento) {
        return new RegistroAdultoRequest("12345678", "JUAN", "PEREZ", nacimiento,
                "juan@example.com", "ClaveSegura#2026", true, false);
    }

    @Test
    void adultoNacidoEn1800SeRechazaComoFechaInvalida() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registrarAdulto(adulto(LocalDate.of(1800, 1, 1)), new byte[]{1}));
        assertEquals("La fecha de nacimiento no es válida.", ex.getMessage());
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void adultoConFechaFuturaSeRechazaComoFechaInvalida() {
        LocalDate manana = LocalDate.now(AR).plusDays(1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.registrarAdulto(adulto(manana), new byte[]{1}));
        assertEquals("La fecha de nacimiento no es válida.", ex.getMessage());
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void menorConFechaFuturaSeRechazaComoFechaInvalida() {
        Usuario ar = new Usuario();
        ar.setId(UUID.randomUUID());
        ar.setTipo(TipoUsuario.ADULTO);
        ar.setCapacidadAdultoResponsable(true);
        RegistroMenorRequest req = new RegistroMenorRequest("12345678", "JUAN", "PEREZ",
                LocalDate.now(AR).plusYears(1), "ClaveSegura#2026", true, "v1");
        assertThrows(IllegalArgumentException.class, () -> service.registrarMenor(req, new byte[]{1}, ar));
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void verificacionPreviaConFechaAbsurdaSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.verificarDocumentoParaRegistro(
                "12345678", "JUAN", "PEREZ", LocalDate.of(1850, 6, 1), new byte[]{1}));
        verify(ocrService, never()).procesarDocumento(any(), any());
    }
}
