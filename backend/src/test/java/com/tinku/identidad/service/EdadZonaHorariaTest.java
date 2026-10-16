package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.ConsentimientoMenorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La edad (mayor de 18 para registrarse como adulto, 6..17 para un menor) se
 * calcula con el "hoy" de Argentina, no con la zona del servidor. El contenedor
 * corre en UTC: de 21:00 a 24:00 (hora argentina) el servidor ya está en el día
 * siguiente y alguien que cumple 18 mañana pasaba como adulto.
 *
 * <p>El test pone la zona de la JVM en una zona cuyo día difiere del de Buenos
 * Aires en este momento (UTC+14 o UTC-12: siempre hay una de las dos) y arma una
 * fecha de nacimiento que cae justo en el borde.
 */
class EdadZonaHorariaTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private TimeZone zonaOriginal;
    private UsuarioRepository usuarioRepo;
    private OcrService ocrService;
    private PasswordEncoder passwordEncoder;
    private UsuarioService service;

    /** Zona de la JVM cuyo día no es el de Argentina; true si va adelantada. */
    private boolean zonaDesfasada;

    @BeforeEach
    void setUp() {
        zonaOriginal = TimeZone.getDefault();
        LocalDate hoyAr = LocalDate.now(AR);
        ZoneId adelantada = ZoneId.of("Pacific/Kiritimati");
        if (!LocalDate.now(adelantada).equals(hoyAr)) {
            TimeZone.setDefault(TimeZone.getTimeZone(adelantada));
            zonaDesfasada = true;
        } else {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Etc/GMT+12")));
            zonaDesfasada = false;
        }
        usuarioRepo = mock(UsuarioRepository.class);
        ocrService = mock(OcrService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UsuarioService(usuarioRepo, ocrService, passwordEncoder,
                mock(OcrBackoffService.class), mock(ConsentimientoMenorRepository.class));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void restaurar() {
        TimeZone.setDefault(zonaOriginal);
    }

    private void ocrDevuelve(LocalDate nacimiento) {
        when(ocrService.procesarDocumento(eq(new byte[]{1}), any()))
                .thenReturn(new ResultadoOcr(true, "12345678", "JUAN", "PEREZ", nacimiento));
    }

    private RegistroAdultoRequest adulto(LocalDate nacimiento) {
        return new RegistroAdultoRequest("12345678", "JUAN", "PEREZ", nacimiento,
                "juan@example.com", "ClaveSegura#2026", true, false);
    }

    @Test
    void registroDeAdultoUsaElHoyDeArgentina() {
        LocalDate hoyAr = LocalDate.now(AR);
        if (zonaDesfasada) {
            // En Argentina cumple 18 MAÑANA: hoy tiene 17 y no puede registrarse como adulto.
            LocalDate nacimiento = hoyAr.plusDays(1).minusYears(18);
            ocrDevuelve(nacimiento);
            assertThrows(EdadInsuficienteException.class,
                    () -> service.registrarAdulto(adulto(nacimiento), new byte[]{1}));
        } else {
            // En Argentina cumple 18 HOY: puede registrarse aunque el servidor siga en ayer.
            LocalDate nacimiento = hoyAr.minusYears(18);
            ocrDevuelve(nacimiento);
            assertDoesNotThrow(() -> service.registrarAdulto(adulto(nacimiento), new byte[]{1}));
        }
    }

    @Test
    void altaDeMenorUsaElHoyDeArgentina() {
        Usuario ar = new Usuario();
        ar.setId(UUID.randomUUID());
        ar.setTipo(TipoUsuario.ADULTO);
        ar.setCapacidadAdultoResponsable(true);
        LocalDate hoyAr = LocalDate.now(AR);
        // Adelantada: cumple 18 mañana en Argentina, hoy sigue siendo menor (17).
        // Atrasada: cumple 18 hoy en Argentina, ya no puede darse de alta como menor.
        LocalDate nacimiento = zonaDesfasada ? hoyAr.plusDays(1).minusYears(18) : hoyAr.minusYears(18);
        ocrDevuelve(nacimiento);
        RegistroMenorRequest req = new RegistroMenorRequest("12345678", "JUAN", "PEREZ",
                nacimiento, "ClaveSegura#2026", true, "v1");
        if (zonaDesfasada) {
            assertDoesNotThrow(() -> service.registrarMenor(req, new byte[]{1}, ar));
        } else {
            assertThrows(EdadInsuficienteException.class,
                    () -> service.registrarMenor(req, new byte[]{1}, ar));
        }
    }

    @Test
    void edadDelUsuarioUsaElHoyDeArgentina() {
        LocalDate hoyAr = LocalDate.now(AR);
        Usuario u = new Usuario();
        if (zonaDesfasada) {
            u.setFechaNacimiento(hoyAr.plusDays(1).minusYears(18));
            assertEquals(17, u.getEdad());
        } else {
            u.setFechaNacimiento(hoyAr.minusYears(18));
            assertEquals(18, u.getEdad());
        }
    }
}
