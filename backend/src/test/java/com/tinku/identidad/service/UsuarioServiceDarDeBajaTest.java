package com.tinku.identidad.service;

import com.tinku.identidad.model.EstadoCuenta;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test (Mockito, sin Spring/Docker) de la baja de menor (FR-ID-014,
 * T-M1-12): solo el Adulto Responsable, y con verificación de reservas futuras.
 *
 * FASE2-06 / AUD-017 (ADR-M1-05): la baja dejó de ser un DELETE y pasó a ser
 * anonimización. Este test codificaba el comportamiento que el finding declara
 * roto (borrar la fila que las FKs de reservas/seguridad/reputacion siguen
 * referenciando) — se ajusta al nuevo contrato: nunca se borra al menor, los
 * datos personales se reemplazan y la transacción persiste con save.
 */
class UsuarioServiceDarDeBajaTest {

    private UsuarioRepository usuarioRepo;
    private ConsentimientoMenorRepository consentRepo;
    private AutorizacionTutorRepository autorizacionRepo;
    private VerificadorReservasFuturas verificador;
    private PasswordEncoder passEncoder;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        usuarioRepo = mock(UsuarioRepository.class);
        consentRepo = mock(ConsentimientoMenorRepository.class);
        autorizacionRepo = mock(AutorizacionTutorRepository.class);
        verificador = mock(VerificadorReservasFuturas.class);
        passEncoder = mock(PasswordEncoder.class);
        when(passEncoder.encode(any())).thenReturn("hash-baja");
        service = new UsuarioService(usuarioRepo, mock(OcrService.class),
                passEncoder, mock(OcrBackoffService.class),
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
        m.setDni("44444444");
        m.setNombre("Sofia");
        m.setApellido("Perez");
        m.setEmail("44444444@tinku.test");
        m.setFechaNacimiento(LocalDate.of(2015, 7, 20));
        m.setPasswordHash("hash-original");
        m.setTipo(TipoUsuario.MENOR);
        m.setAdultoResponsable(ar);
        return m;
    }

    @Test
    void bajaAnonimizaNoBorraNiDatosDelMenorNiElPerfil() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(verificador.contarReservasFuturas(menor.getId())).thenReturn(0L);

        service.darDeBajaMenor(ar, menor.getId(), false);

        verify(autorizacionRepo).deleteByMenorId(menor.getId());
        verify(consentRepo).deleteByMenorId(menor.getId());
        verify(usuarioRepo, never()).delete(any());
        verify(usuarioRepo).save(menor);

        assertThat(menor.getDni()).startsWith("BAJA-").hasSizeLessThanOrEqualTo(20);
        assertThat(menor.getDni()).isNotEqualTo("44444444");
        assertThat(menor.getNombre()).isEqualTo("Perfil");
        assertThat(menor.getApellido()).isEqualTo("dado de baja");
        assertThat(menor.getEmail()).isNull();
        assertThat(menor.getFechaNacimiento()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(menor.getPasswordHash()).isEqualTo("hash-baja");
        assertThat(menor.getEstadoCuenta()).isEqualTo(EstadoCuenta.BAJA);
        assertThat(menor.isActivoParaMatching()).isFalse();
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
    void bajaConConfirmacionPeseAReservasProcedeAnonimizando() {
        Usuario ar = adultoResponsable();
        Usuario menor = menor(ar);
        when(usuarioRepo.findById(menor.getId())).thenReturn(Optional.of(menor));
        when(verificador.contarReservasFuturas(menor.getId())).thenReturn(3L);

        service.darDeBajaMenor(ar, menor.getId(), true); // confirmación explícita

        verify(usuarioRepo, never()).delete(any());
        verify(usuarioRepo).save(menor);
        assertThat(menor.getEstadoCuenta()).isEqualTo(EstadoCuenta.BAJA);
    }
}