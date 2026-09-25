package com.tinku.admin;

import com.tinku.admin.notificacion.EnvioEmailNotificacionesService;
import com.tinku.admin.notificacion.Notificacion;
import com.tinku.admin.notificacion.NotificacionRepository;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.shared.email.EmailEnvioException;
import com.tinku.shared.email.EnviadorEmail;
import com.tinku.shared.email.MensajeEmail;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FASE2-03 + ADR-000-06: el barrido lee el outbox y manda por email lo que va por
 * email, con backoff 5/15/60 min (Tabla de Tiempos) y fail-closed sin proveedor.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EnvioEmailNotificacionesIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired EnvioEmailNotificacionesService envio;
    @Autowired NotificacionRepository repo;
    @Autowired UsuarioRepository usuarioRepository;
    @MockitoBean EnviadorEmail enviador;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @BeforeEach
    void limpiar() {
        repo.deleteAll();
        reset(enviador);
        when(enviador.configurado()).thenReturn(true);
    }

    private Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 44_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Ana");
        u.setApellido("Paz");
        u.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        u.setTipo(TipoUsuario.ADULTO);
        u.setPasswordHash("hash");
        u.setEmail(email);
        u.setCapacidadEstudiante(true);
        return usuarioRepository.save(u);
    }

    private Notificacion pendiente(Usuario u, TipoNotificacion tipo, Map<String, String> datos) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(u.getId());
        n.setTipo(tipo);
        n.setDatos(datos);
        n.setProximoIntentoEmailAt(Instant.now().minusSeconds(1));
        return repo.save(n);
    }

    private Notificacion recargar(Notificacion n) {
        return repo.findById(n.getId()).orElseThrow();
    }

    private void vencer(Notificacion n) {
        Notificacion f = recargar(n);
        f.setProximoIntentoEmailAt(Instant.now().minusSeconds(1));
        repo.save(f);
    }

    @Test
    void killswitchMenor_seMandaAlAdultoResponsable_sinDatosDeLaClase() {
        Usuario ar = usuario("ar" + CONTADOR.get() + "@example.com");
        Notificacion n = pendiente(ar, TipoNotificacion.KILLSWITCH_MENOR,
                Map.of("sesionId", UUID.randomUUID().toString(), "fecha", Instant.now().toString()));

        assertThat(envio.procesarPendientes()).isEqualTo(1);

        ArgumentCaptor<MensajeEmail> msg = ArgumentCaptor.forClass(MensajeEmail.class);
        verify(enviador).enviar(msg.capture());
        assertThat(msg.getValue().para()).isEqualTo(ar.getEmail());
        assertThat(msg.getValue().texto()).contains("/cuenta/notificaciones");
        assertThat(recargar(n).getEnviadaEmailAt()).isNotNull();
        // Ya enviada: un segundo barrido no la reenvía.
        assertThat(envio.procesarPendientes()).isZero();
    }

    @Test
    void denunciaRecibida_diceHastaCuandoHayDescargo() {
        Usuario denunciado = usuario("den" + CONTADOR.get() + "@example.com");
        pendiente(denunciado, TipoNotificacion.DENUNCIA_RECIBIDA, Map.of(
                "denunciaId", UUID.randomUUID().toString(),
                "descargoVenceAt", "2026-10-01T15:00:00Z"));

        envio.procesarPendientes();

        ArgumentCaptor<MensajeEmail> msg = ArgumentCaptor.forClass(MensajeEmail.class);
        verify(enviador).enviar(msg.capture());
        // 15:00 UTC = 12:00 en Argentina.
        assertThat(msg.getValue().texto()).contains("01/10 12:00").contains("/cuenta/seguridad");
    }

    @Test
    void fallaDelProveedor_reintentaA5_15y60min_yDespuesDescarta() {
        Usuario u = usuario("r" + CONTADOR.get() + "@example.com");
        Notificacion n = pendiente(u, TipoNotificacion.DENUNCIA_RECIBIDA,
                Map.of("denunciaId", "x", "descargoVenceAt", Instant.now().toString()));
        doThrow(new EmailEnvioException("caído")).when(enviador).enviar(any());

        int[] esperadoMin = {5, 15, 60};
        for (int i = 0; i < 3; i++) {
            Instant antes = Instant.now();
            envio.procesarPendientes();
            Notificacion f = recargar(n);
            assertThat(f.getIntentosEmail()).isEqualTo(i + 1);
            assertThat(f.getProximoIntentoEmailAt()).isBetween(
                    antes.plus(Duration.ofMinutes(esperadoMin[i])).minusSeconds(5),
                    antes.plus(Duration.ofMinutes(esperadoMin[i])).plusSeconds(30));
            assertThat(f.getEmailDescartadoAt()).isNull();
            vencer(n);
        }
        envio.procesarPendientes();

        Notificacion agotada = recargar(n);
        assertThat(agotada.getIntentosEmail()).isEqualTo(4);
        assertThat(agotada.getEmailDescartadoAt()).isNotNull();
        assertThat(agotada.getEnviadaEmailAt()).isNull();
        assertThat(envio.procesarPendientes()).isZero();
    }

    @Test
    void sinProveedorConfigurado_noEnviaYQuedaPendiente() {
        when(enviador.configurado()).thenReturn(false);
        Notificacion n = pendiente(usuario("s" + CONTADOR.get() + "@example.com"), TipoNotificacion.KILLSWITCH_MENOR,
                Map.of("sesionId", "x", "fecha", Instant.now().toString()));

        assertThat(envio.procesarPendientes()).isZero();

        verify(enviador, never()).enviar(any());
        assertThat(recargar(n).getEnviadaEmailAt()).isNull();
        assertThat(recargar(n).getEmailDescartadoAt()).isNull();
        assertThat(recargar(n).getIntentosEmail()).isZero();
    }

    @Test
    void destinatarioSinEmail_seDescartaSinLlamarAlProveedor() {
        Notificacion n = pendiente(usuario(null), TipoNotificacion.KILLSWITCH_MENOR,
                Map.of("sesionId", "x", "fecha", Instant.now().toString()));

        envio.procesarPendientes();

        verify(enviador, never()).enviar(any());
        assertThat(recargar(n).getEmailDescartadoAt()).isNotNull();
    }

    @Test
    void noVencida_noSeProcesa() {
        Notificacion n = pendiente(usuario("n" + CONTADOR.get() + "@example.com"), TipoNotificacion.KILLSWITCH_MENOR,
                Map.of("sesionId", "x", "fecha", Instant.now().toString()));
        Notificacion f = recargar(n);
        f.setProximoIntentoEmailAt(Instant.now().plus(Duration.ofMinutes(5)));
        repo.save(f);

        assertThat(envio.procesarPendientes()).isZero();
        verify(enviador, never()).enviar(any());
    }
}
