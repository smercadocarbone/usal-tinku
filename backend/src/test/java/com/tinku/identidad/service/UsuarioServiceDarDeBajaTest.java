package com.tinku.identidad.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.port.VerificadorReservasFuturas;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.ConsentimientoMenorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, sin Spring/Docker) de la baja de menor (FR-ID-014,
 * T-M1-12): solo el Adulto Responsable, y con verificación de reservas futuras.
 */
class UsuarioServiceDarDeBajaTest {

    private UsuarioRepository usuarioRepo;
    private ConsentimientoMenorRepository consentRepo;
    private AutorizacionTutorRepository autorizacionRepo;
    private VerificadorReservasFuturas verificador;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarioRepo = mock(UsuarioRepository.class);
        consentRepo = mock(ConsentimientoMenorRepository.class);
        autorizacionRepo = mock(AutorizacionTutorRepository.class);
        verificador = mock(VerificadorReservasFuturas.class);
        service = new UsuarioService(usuarioRepo, mock(OcrService.class),
                mock(PasswordEncoder.class), mock(OcrBackoffService.class),
                consentRepo, autorizacionRepo, verificador);
    }

    private Usuario adultoResponsable() {
        Usuario a = new Usuario();
        a.setId(UUID.randomUUID());
        a.setTipo(TipoUsuario.ADULTO);
        return a;
    }

    private Usuario menor(Usuario ar) {
        Usuario m = new Usuario();
        m.setId(UUID.randomUUID());
        m.setTipo(TipoUsuario.MENOR);
        m.setAdultoResponsable(ar);
        return m;
    }

    @Test
    void bajaSinReservasEliminaPerfilYDatos() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(verificador.contarReservasFuturas(menor.getId())).thenReturn(0L);

        service.darDeBajaMenor(ar, menor.getId(), false);

        verify(autorizacionRepo).deleteByMenorId(menor.getId());
        verify(consentRepo).deleteByMenorId(menor.getId());
        verify(usuarioRepo).delete(menor);
    }

    @Test
    void menorNoACargoRechaza() {
        Usuario ar = adultoResponsable();
        Usuario otroAr = adultoResponsable();
        Usuario menor = menor(otroAr);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));

        assertThrows(MenorNoPerteneceException.class,
                () -> service.darDeBajaMenor(ar, menor.getId(), true));
        verify(usuarioRepo, never()).delete(any());
    }

    @Test
    void bajaSinConfirmacionConReservasFuturasRechaza() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(verificador.contarReservasFuturas(menor.getId())).thenReturn(3L);

        assertThrows(ReservasFuturasPendientesException.class,
                () -> service.darDeBajaMenor(ar, menor.getId(), false)); // FR-ID-014
        verify(usuarioRepo, never()).delete(any());
    }

    @Test
    void bajaConConfirmacionPeseAReservasProcede() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(verificador.contarReservasFuturas(menor.getId())).thenReturn(3L);

        service.darDeBajaMenor(ar, menor.getId(), true); // confirmación explícita

        verify(usuarioRepo).delete(menor);
    }
}
