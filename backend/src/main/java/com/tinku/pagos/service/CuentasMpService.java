package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.CuentaMpTutor;
import com.tinku.pagos.model.EstadoCuentaMp;
import com.tinku.pagos.model.OAuthEstadoMp;
import com.tinku.pagos.port.MercadoPagoOAuthClient;
import com.tinku.pagos.port.MercadoPagoOAuthClient.TokensMp;
import com.tinku.pagos.repository.CuentaMpTutorRepository;
import com.tinku.pagos.repository.OAuthEstadoMpRepository;
import com.tinku.reservas.port.VerificadorCobroTutor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.SoloTutorException;
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conexión de MercadoPago de cada Tutor por OAuth (ADR-M5-02, modelo A) y resolución del token
 * del vendedor para cada operación de cobro.
 *
 * <p><b>Cuándo está activo:</b> solo si está configurada la app de marketplace
 * ({@code MP_CLIENT_ID}). Sin ella (dev, tests, piloto antes de crear la app), todo sigue con el
 * token de la plataforma y ningún Tutor queda bloqueado por no tener cuenta conectada.</p>
 */
@Service
public class CuentasMpService implements VerificadorCobroTutor {

    /** Tabla de Tiempos: se renuevan los tokens que vencen en menos de 30 días. */
    public static final Duration MARGEN_REFRESCO = Duration.ofDays(30);
    static final Duration VIGENCIA_STATE = Duration.ofMinutes(10);

    private static final Logger LOG = LoggerFactory.getLogger(CuentasMpService.class);
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final CuentaMpTutorRepository cuentaRepo;
    private final OAuthEstadoMpRepository estadoRepo;
    private final ReservaRepository reservaRepo;
    private final MercadoPagoOAuthClient oauth;
    private final CifradorTokens cifrador;
    private final Notificador notificador;
    private final JdbcTemplate jdbc;
    private final String clientId;
    private final String urlAutorizacion;
    private final String redirectUri;
    private final SecureRandom random = new SecureRandom();

    public CuentasMpService(CuentaMpTutorRepository cuentaRepo,
                            OAuthEstadoMpRepository estadoRepo,
                            ReservaRepository reservaRepo,
                            MercadoPagoOAuthClient oauth,
                            CifradorTokens cifrador,
                            Notificador notificador,
                            JdbcTemplate jdbc,
                            @Value("${tinku.mercadopago.oauth.client-id:}") String clientId,
                            @Value("${tinku.mercadopago.oauth.url-autorizacion:https://auth.mercadopago.com.ar/authorization}")
                            String urlAutorizacion,
                            @Value("${tinku.mercadopago.oauth.redirect-uri:}") String redirectUri) {
        this.cuentaRepo = cuentaRepo;
        this.estadoRepo = estadoRepo;
        this.reservaRepo = reservaRepo;
        this.oauth = oauth;
        this.cifrador = cifrador;
        this.notificador = notificador;
        this.jdbc = jdbc;
        this.clientId = clientId;
        this.urlAutorizacion = urlAutorizacion;
        this.redirectUri = redirectUri;
    }

    public boolean oauthHabilitado() {
        return clientId != null && !clientId.isBlank();
    }

    // ------------------------------------------------------------------ conexión

    /** URL de autorización de MP con {@code state} (CSRF) y PKCE S256. Solo un Tutor. */
    @Transactional
    public String iniciarConexion(Usuario tutor) {
        exigirTutor(tutor);
        if (!oauthHabilitado() || !cifrador.configurado()) {
            throw new MercadoPagoNoConfiguradoException();
        }
        estadoRepo.borrarVencidos(Instant.now());
        OAuthEstadoMp estado = new OAuthEstadoMp();
        estado.setState(aleatorio(32));
        estado.setTutorId(tutor.getId());
        estado.setCodeVerifier(aleatorio(48));
        estado.setExpiraAt(Instant.now().plus(VIGENCIA_STATE));
        estadoRepo.save(estado);
        return UriComponentsBuilder.fromUriString(urlAutorizacion)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("platform_id", "mp")
                .queryParam("state", estado.getState())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", desafio(estado.getCodeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }

    /**
     * Vuelta de MP: valida el {@code state} (uno solo, vigente), canjea el código y guarda los
     * tokens cifrados. El Tutor sale del {@code state}, nunca de un parámetro del navegador.
     */
    @Transactional
    public UUID completarConexion(String state, String code) {
        OAuthEstadoMp estado = state == null ? null : estadoRepo.findById(state).orElse(null);
        if (estado == null || estado.getExpiraAt().isBefore(Instant.now()) || code == null || code.isBlank()) {
            throw new CuentaMpException(HttpStatus.BAD_REQUEST, "La conexión con MercadoPago venció o no es válida.");
        }
        estadoRepo.delete(estado);
        TokensMp tokens = oauth.canjearCodigo(code, estado.getCodeVerifier(), redirectUri);
        cuentaRepo.findFirstByMpUserIdAndEstado(tokens.userId(), EstadoCuentaMp.CONECTADA)
                .filter(otra -> !otra.getTutorId().equals(estado.getTutorId()))
                .ifPresent(otra -> {
                    throw new CuentaMpException(HttpStatus.CONFLICT,
                            "Esa cuenta de MercadoPago ya está conectada a otro tutor.");
                });
        CuentaMpTutor cuenta = cuentaRepo.findById(estado.getTutorId()).orElseGet(CuentaMpTutor::new);
        cuenta.setTutorId(estado.getTutorId());
        cuenta.setConectadaAt(Instant.now());
        guardarTokens(cuenta, tokens);
        return estado.getTutorId();
    }

    /**
     * Desconectar: bloqueado (409) mientras Tinku todavía pueda necesitar reembolsar con el token
     * del Tutor — reservas pendientes o futuras, o escrows todavía en la ventana (ADR-M5-02).
     */
    @Transactional
    public void desconectar(Usuario tutor) {
        exigirTutor(tutor);
        CuentaMpTutor cuenta = cuentaRepo.findById(tutor.getId())
                .orElseThrow(() -> new CuentaMpException(HttpStatus.NOT_FOUND, "No tenés MercadoPago conectado."));
        if (tieneVentanasAbiertas(tutor.getId())) {
            throw new CuentaMpException(HttpStatus.CONFLICT,
                    "Tenés clases por dar o cobros todavía en revisión. Podés desconectar MercadoPago "
                            + "cuando terminen.");
        }
        cuenta.setEstado(EstadoCuentaMp.REVOCADA);
        cuenta.setUpdatedAt(Instant.now());
        cuentaRepo.save(cuenta);
    }

    @Transactional(readOnly = true)
    public EstadoConexion estado(Usuario tutor) {
        exigirTutor(tutor);
        return cuentaRepo.findById(tutor.getId())
                .map(c -> new EstadoConexion(oauthHabilitado(), c.getEstado().name(), c.getConectadaAt()))
                .orElse(new EstadoConexion(oauthHabilitado(), null, null));
    }

    public record EstadoConexion(boolean requerida, String estado, Instant conectadaAt) {
    }

    // ------------------------------------------------------------------ tokens

    /** Token del vendedor para cobrar a nombre de este Tutor; {@code null} = token de la plataforma. */
    @Transactional(readOnly = true)
    public String tokenParaTutor(UUID tutorId) {
        if (!oauthHabilitado()) {
            return null;
        }
        return cuentaRepo.findById(tutorId)
                .filter(c -> c.getEstado() == EstadoCuentaMp.CONECTADA)
                .map(c -> cifrador.descifrar(c.getAccessTokenCifrado()))
                .orElseThrow(CuentaMpException::tutorSinCuenta);
    }

    @Transactional(readOnly = true)
    public String tokenParaReserva(UUID reservaId) {
        if (!oauthHabilitado()) {
            return null;
        }
        UUID tutorId = reservaRepo.findById(reservaId)
                .map(r -> r.getTutor().getId())
                .orElseThrow(CuentaMpException::tutorSinCuenta);
        return tokenParaTutor(tutorId);
    }

    /** Token para operar un pago ya registrado (reembolsos, verificación de liberación). */
    @Transactional(readOnly = true)
    public String tokenParaTransaccion(com.tinku.pagos.model.Transaccion transaccion) {
        return transaccion.isEnBypass() ? null : tokenParaReserva(transaccion.getReservaId());
    }

    /** Webhook: el aviso trae el {@code user_id} del vendedor. Desconocido → token de la plataforma. */
    @Transactional(readOnly = true)
    public String tokenParaMpUserId(String mpUserId) {
        if (!oauthHabilitado() || mpUserId == null || mpUserId.isBlank()) {
            return null;
        }
        return cuentaRepo.findFirstByMpUserIdAndEstado(mpUserId, EstadoCuentaMp.CONECTADA)
                .map(c -> cifrador.descifrar(c.getAccessTokenCifrado()))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean puedeCobrar(UUID tutorId) {
        return !oauthHabilitado() || cuentaRepo.findById(tutorId)
                .map(c -> c.getEstado() == EstadoCuentaMp.CONECTADA).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> quienesPuedenCobrar(Collection<UUID> tutores) {
        if (!oauthHabilitado() || tutores.isEmpty()) {
            return new HashSet<>(tutores);
        }
        return new HashSet<>(cuentaRepo.conectadasEntre(tutores));
    }

    // ------------------------------------------------------------------ refresco

    /**
     * Job diario: renueva los tokens que vencen pronto. Un fallo deja la cuenta en ERROR y avisa
     * (el aviso es outbox: necesita esta transacción; los fallos de MP se capturan por cuenta).
     */
    @Transactional
    public int refrescarPorVencer() {
        if (!oauthHabilitado()) {
            return 0;
        }
        int renovadas = 0;
        for (CuentaMpTutor cuenta : cuentaRepo.findByEstadoAndExpiraAtBefore(
                EstadoCuentaMp.CONECTADA, Instant.now().plus(MARGEN_REFRESCO))) {
            if (refrescar(cuenta.getTutorId())) {
                renovadas++;
            }
        }
        return renovadas;
    }

    @Transactional
    public boolean refrescar(UUID tutorId) {
        CuentaMpTutor cuenta = cuentaRepo.findById(tutorId).orElse(null);
        if (cuenta == null || cuenta.getEstado() != EstadoCuentaMp.CONECTADA) {
            return false;
        }
        try {
            if (cuenta.getRefreshTokenCifrado() == null) {
                throw new MercadoPagoNoDisponibleException();
            }
            guardarTokens(cuenta, oauth.refrescar(cifrador.descifrar(cuenta.getRefreshTokenCifrado())));
            return true;
        } catch (RuntimeException e) {
            LOG.warn("No se pudo renovar el token de MercadoPago del tutor {}", tutorId, e);
            cuenta.setEstado(EstadoCuentaMp.ERROR);
            cuenta.setUpdatedAt(Instant.now());
            cuentaRepo.save(cuenta);
            notificador.notificar(tutorId, TipoNotificacion.MP_CUENTA_DESCONECTADA, Map.of());
            return false;
        }
    }

    // ------------------------------------------------------------------ privados

    private void guardarTokens(CuentaMpTutor cuenta, TokensMp tokens) {
        cuenta.setMpUserId(tokens.userId());
        cuenta.setAccessTokenCifrado(cifrador.cifrar(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            cuenta.setRefreshTokenCifrado(cifrador.cifrar(tokens.refreshToken()));
        }
        cuenta.setPublicKey(tokens.publicKey());
        cuenta.setExpiraAt(Instant.now().plusSeconds(tokens.expiresInSegundos() > 0
                ? tokens.expiresInSegundos() : Duration.ofDays(180).toSeconds()));
        cuenta.setEstado(EstadoCuentaMp.CONECTADA);
        cuenta.setUpdatedAt(Instant.now());
        cuentaRepo.save(cuenta);
    }

    private boolean tieneVentanasAbiertas(UUID tutorId) {
        Integer reservas = jdbc.queryForObject("""
                SELECT count(*) FROM reservas.reservas
                 WHERE tutor_id = ? AND estado IN ('pendiente_pago', 'confirmada', 'en_curso')""",
                Integer.class, tutorId);
        Integer escrows = jdbc.queryForObject("""
                SELECT count(*) FROM pagos.transacciones t JOIN reservas.reservas r ON r.id = t.reserva_id
                 WHERE r.tutor_id = ? AND t.estado IN ('retenido_escrow', 'pausado_denuncia', 'pausado_alerta')""",
                Integer.class, tutorId);
        return (reservas != null && reservas > 0) || (escrows != null && escrows > 0);
    }

    private static void exigirTutor(Usuario usuario) {
        if (usuario.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo un tutor conecta su cuenta de MercadoPago.");
        }
    }

    private String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return B64URL.encodeToString(b);
    }

    private static String desafio(String verifier) {
        try {
            return B64URL.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
