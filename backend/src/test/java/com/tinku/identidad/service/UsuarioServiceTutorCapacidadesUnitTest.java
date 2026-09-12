package com.tinku.identidad.service;

import com.tinku.identidad.dto.ActualizarCapacidadesRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Docker/Tesseract) de {@link UsuarioService} para
 * T-M1-09 (alta de Tutor, FR-ID-007) y T-M1-08 (capacidades combinables,
 * FR-ID-015/016).
 */
class UsuarioServiceTutorCapacidadesUnitTest {

    private UsuarioRepository usuarioRepo;
    private OcrService ocrService;
    private PasswordEncoder passwordEncoder;
    private OcrBackoffService ocrBackoffService;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarioRepo = mock(UsuarioRepository.class);
        ocrService = mock(OcrService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        ocrBackoffService = mock(OcrBackoffService.class);
        ConsentimientoMenorRepository consentimientoRepo = mock(ConsentimientoMenorRepository.class);
        service = new UsuarioService(usuarioRepo, ocrService, passwordEncoder,
                ocrBackoffService, consentimientoRepo);
    }

    private RegistroTutorRequest tutorRequest() {
        return new RegistroTutorRequest(
                "12345678", "JUAN", "PEREZ", LocalDate.of(1990, 1, 1),
                "12345678@tinku.test", "password123");
    }

    @Test
    void registrarTutorExitoso() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(1990, 1, 1))); // coincide con la declarada en tutorRequest()
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario tutor = service.registrarTutor(tutorRequest(), new byte[]{1});

        assertEquals(TipoUsuario.TUTOR, tutor.getTipo());
        assertEquals("12345678", tutor.getDni());
        assertEquals("JUAN", tutor.getNombre());
        assertEquals(EstadoCuenta.ACTIVA, tutor.getEstadoCuenta());
        verify(usuarioRepo).save(any());
    }

    @Test
    void registrarTutorMenorDe18RechazaSinExcepciones() {
        RegistroTutorRequest menor = new RegistroTutorRequest(
                "12345678", "JUAN", "PEREZ", LocalDate.now().minusYears(15),
                "12345678@tinku.test", "password123");
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.now().minusYears(15))); // 15 años, coherente con lo declarado

        assertThrows(EdadInsuficienteException.class,
                () -> service.registrarTutor(menor, new byte[]{1}));
        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void registrarTutorDocumentoIlegibleConsumeBackoff() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any())).thenReturn(ResultadoOcr.ilegible());

        assertThrows(DocumentoIlegibleException.class,
                () -> service.registrarTutor(tutorRequest(), new byte[]{1}));
        verify(ocrBackoffService).registrarIntentoFallido("12345678"); // FR-ID-011
    }

    @Test
    void registrarTutorNombreNoCoincideRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "OTRO", "PEREZ",
                        LocalDate.of(1990, 1, 1)));

        assertThrows(DocumentoNoCoincideException.class,
                () -> service.registrarTutor(tutorRequest(), new byte[]{1}));
    }

    @Test
    void registrarTutorDniDuplicadoRechaza() {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ",
                        LocalDate.of(1990, 1, 1)));
        when(usuarioRepo.existsByDni("12345678")).thenReturn(true);

        assertThrows(DniYaRegistradoException.class,
                () -> service.registrarTutor(tutorRequest(), new byte[]{1}));
    }

    // ---- T-M1-08: capacidades combinables ----

    private Usuario adulto(boolean estudiante, boolean adultoResp) {
        Usuario a = new Usuario();
        a.setId(UUID.randomUUID());
        a.setDni("11111111");
        a.setNombre("MARIA");
        a.setApellido("LOPEZ");
        a.setTipo(TipoUsuario.ADULTO);
        a.setCapacidadEstudiante(estudiante);
        a.setCapacidadAdultoResponsable(adultoResp);
        return a;
    }

    @Test
    void activaCapacidadComplementariaInmediata() {
        Usuario usuario = adulto(true, false);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario result = service.actualizarCapacidades(usuario,
                new ActualizarCapacidadesRequest(true, true));

        assertTrue(result.isCapacidadAdultoResponsable());
        assertTrue(result.isCapacidadEstudiante());
        // FR-ID-015: no se debe necesitar re-OCR (no hay ninguna llamada a ocr).
        verify(ocrService, never()).procesarDocumento(any(), any());
    }

    @Test
    void quedarSinCapacidadesRechaza() {
        Usuario usuario = adulto(true, false);

        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarCapacidades(usuario,
                        new ActualizarCapacidadesRequest(false, false))); // FR-ID-001
    }

    @Test
    void desactivarAdultoResponsableConMenoresACargoRechaza() {
        Usuario usuario = adulto(true, true);
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(usuario.getId(), TipoUsuario.MENOR))
                .thenReturn(2L);

        assertThrows(NoPuedeDesactivarAdultoResponsableException.class,
                () -> service.actualizarCapacidades(usuario,
                        new ActualizarCapacidadesRequest(true, false))); // FR-ID-016
    }

    @Test
    void desactivarAdultoResponsableSinMenoresOK() {
        Usuario usuario = adulto(true, true);
        when(usuarioRepo.countByAdultoResponsableIdAndTipo(usuario.getId(), TipoUsuario.MENOR))
                .thenReturn(0L);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario result = service.actualizarCapacidades(usuario,
                new ActualizarCapacidadesRequest(true, false));

        assertFalse(result.isCapacidadAdultoResponsable());
        assertTrue(result.isCapacidadEstudiante());
    }

    @Test
    void menorNoPuedeSerAdultoResponsable() {
        Usuario menor = adulto(true, false);
        menor.setTipo(TipoUsuario.MENOR);

        assertThrows(IllegalArgumentException.class,
                () -> service.actualizarCapacidades(menor,
                        new ActualizarCapacidadesRequest(true, true))); // Artículo II
    }
}
