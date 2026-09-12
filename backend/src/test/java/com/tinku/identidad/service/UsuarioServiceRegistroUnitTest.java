package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.model.EstadoCuenta;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) de {@link UsuarioService} sobre el
 * alta de MENOR (T-M1-06) y el cableado del backoff de OCR (FR-ID-011).
 * Usa un {@link OcrService} mockeado, NO el Stub real.
 */
class UsuarioServiceRegistroUnitTest {

    private UsuarioRepository usuarioRepo;
    private OcrService ocrService;
    private PasswordEncoder passwordEncoder;
    private OcrBackoffService ocrBackoffService;
    private ConsentimientoMenorRepository consentimientoRepo;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarioRepo = mock(UsuarioRepository.class);
        ocrService = mock(OcrService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        ocrBackoffService = mock(OcrBackoffService.class);
        consentimientoRepo = mock(ConsentimientoMenorRepository.class);
        service = new UsuarioService(usuarioRepo, ocrService, passwordEncoder,
                ocrBackoffService, consentimientoRepo);
    }

    private Usuario adulto() {
        Usuario a = new Usuario();
        a.setId(UUID.randomUUID());
        a.setDni("11111111");
        a.setNombre("MARIA");
        a.setApellido("LOPEZ");
        a.setTipo(TipoUsuario.ADULTO);
        return a;
    }

    private RegistroMenorRequest request() {
        return new RegistroMenorRequest(
                "12345678", "JUAN", "PEREZ", LocalDate.of(2014, 5, 5),
                "password123", true, "v1");
    }

    private RegistroMenorRequest requestConFecha(LocalDate fecha) {
        return new RegistroMenorRequest(
                "12345678", "JUAN", "PEREZ", fecha,
                "password123", true, "v1");
    }

    @Test
    void registrarMenorExitosoCreaMenorYConsentimiento() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(2014, 5, 5))); // 12 años
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(consentimientoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Usuario menor = service.registrarMenor(request(), new byte[]{1}, adulto());

        assertEquals(TipoUsuario.MENOR, menor.getTipo());
        assertEquals("JUAN", menor.getNombre());
        assertEquals("12345678", menor.getDni());
        assertFalse(menor.isCapacidadAdultoResponsable());
        assertEquals(EstadoCuenta.ACTIVA, menor.getEstadoCuenta());
        verify(consentimientoRepo).save(any()); // BR-CONSENT-01 persistido en la misma tx
        verify(usuarioRepo).save(any());
    }

    @Test
    void registrarMenorEdadMenorA6Rechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.now().minusYears(5))); // 5 años
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);

        assertThrows(EdadInsuficienteException.class,
                () -> service.registrarMenor(requestConFecha(LocalDate.now().minusYears(5)), new byte[]{1}, adulto()));
        verify(consentimientoRepo, never()).save(any());
    }

    @Test
    void registrarMenorEdadMayorOIgualA18Rechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.now().minusYears(19))); // 19 años
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);

        assertThrows(EdadInsuficienteException.class,
                () -> service.registrarMenor(requestConFecha(LocalDate.now().minusYears(19)), new byte[]{1}, adulto()));
    }

    @Test
    void registrarMenorSinConsentimientoRechaza() {
        RegistroMenorRequest sinConsentimiento =
                new RegistroMenorRequest("12345678", "JUAN", "PEREZ",
                        LocalDate.of(2014, 5, 5), "password123", false, "v1");

        assertThrows(ConsentimientoNoOtorgadoException.class,
                () -> service.registrarMenor(sinConsentimiento, new byte[]{1}, adulto()));
        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void registrarMenorAlcanzaLimiteDe5Rechaza() {
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(5L);

        assertThrows(LimiteMenoresAlcanzadoException.class,
                () -> service.registrarMenor(request(), new byte[]{1}, adulto()));
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void registrarMenorDocumentoIlegibleConsumeBackoff() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any())).thenReturn(ResultadoOcr.ilegible());
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);

        assertThrows(DocumentoIlegibleException.class,
                () -> service.registrarMenor(request(), new byte[]{1}, adulto()));
        verify(ocrBackoffService).registrarIntentoFallido("12345678"); // FR-ID-011
    }

    @Test
    void registrarMenorNombreNoCoincideRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "OTRO", "PEREZ",
                        LocalDate.of(2014, 5, 5)));
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);

        assertThrows(DocumentoNoCoincideException.class,
                () -> service.registrarMenor(request(), new byte[]{1}, adulto()));
    }

    @Test
    void registrarMenorDniYaRegistradoRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(2014, 5, 5)));
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);
        when(usuarioRepo.existsByDni("12345678")).thenReturn(true);

        assertThrows(DniYaRegistradoException.class,
                () -> service.registrarMenor(request(), new byte[]{1}, adulto()));
    }

    @Test
    void registrarAdultoRespetaBackoffAntesDeOcr() {
        org.mockito.Mockito.doThrow(
                        new DocumentoEnBackoffException(java.time.Duration.ofHours(3)))
                .when(ocrBackoffService).chequearPuedeIntentar("12345678");

        com.tinku.identidad.dto.RegistroAdultoRequest adult =
                new com.tinku.identidad.dto.RegistroAdultoRequest(
                        "12345678", "JUAN", "PEREZ", LocalDate.of(1990, 1, 1),
                        "12345678@tinku.test", "password123", true, false);

        assertThrows(DocumentoEnBackoffException.class,
                () -> service.registrarAdulto(adult, new byte[]{1}));
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void registrarMenorFechaNacimientoNoCoincideRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(2013, 1, 1))); // distinta a la declarada (2014-05-05)
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(any(), eq(TipoUsuario.MENOR)))
                .thenReturn(0L);

        assertThrows(DocumentoNoCoincideException.class,
                () -> service.registrarMenor(request(), new byte[]{1}, adulto()));
    }

    @Test
    void registrarAdultoDniDeclaradoNoCoincideRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "99999999", "JUAN", "PEREZ",
                        LocalDate.of(1990, 1, 1))); // el documento tiene otro número
        com.tinku.identidad.dto.RegistroAdultoRequest adult =
                new com.tinku.identidad.dto.RegistroAdultoRequest(
                        "12345678", "JUAN", "PEREZ", LocalDate.of(1990, 1, 1),
                        "12345678@tinku.test", "password123", true, false);

        assertThrows(DocumentoNoCoincideException.class,
                () -> service.registrarAdulto(adult, new byte[]{1}));
        verify(ocrBackoffService, never()).registrarIntentoFallido("12345678");
    }

    @Test
    void registroConOcrNoDisponiblePropagaSinConsumirBackoff() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenThrow(new OcrNoDisponibleException());
        com.tinku.identidad.dto.RegistroAdultoRequest adult =
                new com.tinku.identidad.dto.RegistroAdultoRequest(
                        "12345678", "JUAN", "PEREZ", LocalDate.of(1990, 1, 1),
                        "12345678@tinku.test", "password123", true, false);

        assertThrows(OcrNoDisponibleException.class,
                () -> service.registrarAdulto(adult, new byte[]{1}));
        // No es culpa del usuario: no consume el ciclo de reintentos (FR-ID-011).
        verify(ocrBackoffService, never()).registrarIntentoFallido("12345678");
    }

    @Test
    void registrarAdultoCoincideAunqueElDocumentoEsteEnMayusculas() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(1990, 1, 1))); // documento en MAYÚSCULAS
        com.tinku.identidad.dto.RegistroAdultoRequest adult =
                new com.tinku.identidad.dto.RegistroAdultoRequest(
                        "12345678", "juan", "Perez", LocalDate.of(1990, 1, 1),
                        "12345678@tinku.test", "password123", true, false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario creado = service.registrarAdulto(adult, new byte[]{1});

        assertEquals(TipoUsuario.ADULTO, creado.getTipo());
        assertEquals("12345678", creado.getDni());
    }
}
