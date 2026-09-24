package com.tinku.identidad.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FASE2-02 / AUD-012: bloqueo por intentos fallidos de login, por DNI
 * (Tabla_Tiempos_Tinku.md: 5 intentos; bloqueo de 15 min que se duplica en cada
 * bloqueo consecutivo, con tope de 24 hs).
 *
 * <ul>
 *   <li><strong>En memoria</strong>, coherente con D8 y sin migración: un reinicio
 *       olvida los contadores (falla benigna). Instancia única (ADR-000-04).</li>
 *   <li>La clave es el DNI tal cual lo mandó el cliente, exista o no: un DNI
 *       inexistente se bloquea igual y con el mismo mensaje, así el bloqueo no
 *       sirve para enumerar DNIs registrados.</li>
 *   <li>Durante el bloqueo se rechaza incluso la contraseña correcta.</li>
 *   <li>Un login exitoso resetea el contador y la escala de bloqueos.</li>
 * </ul>
 * Mismo contrato que {@link OcrBackoffService}: chequear, registrar fallo, registrar éxito.
 */
@Service
public class LoginBackoffService {

    private final int intentosAntesDeBloqueo;
    private final Duration bloqueoInicial;
    private final Duration bloqueoMaximo;
    private final Clock reloj;
    private final Map<String, Estado> estados = new ConcurrentHashMap<>();

    private static final class Estado {
        int fallos;
        int bloqueosConsecutivos;
        Instant bloqueadoHasta;
    }

    @Autowired
    public LoginBackoffService(
            @Value("${tinku.login.intentos-antes-de-bloqueo:5}") int intentosAntesDeBloqueo,
            @Value("${tinku.login.bloqueo-inicial-minutos:15}") long bloqueoInicialMinutos,
            @Value("${tinku.login.bloqueo-maximo-horas:24}") long bloqueoMaximoHoras) {
        this(intentosAntesDeBloqueo, Duration.ofMinutes(bloqueoInicialMinutos),
                Duration.ofHours(bloqueoMaximoHoras), Clock.systemUTC());
    }

    LoginBackoffService(int intentos, Duration inicial, Duration maximo, Clock reloj) {
        this.intentosAntesDeBloqueo = intentos;
        this.bloqueoInicial = inicial;
        this.bloqueoMaximo = maximo;
        this.reloj = reloj;
    }

    /** Lanza {@link LoginBloqueadoException} si el DNI está en período de bloqueo. */
    public void chequearPuedeIntentar(String dni) {
        Estado e = estados.get(clave(dni));
        if (e == null) return;
        synchronized (e) {
            Instant ahora = reloj.instant();
            if (e.bloqueadoHasta != null && ahora.isBefore(e.bloqueadoHasta)) {
                throw new LoginBloqueadoException(Duration.between(ahora, e.bloqueadoHasta));
            }
        }
    }

    public void registrarFallo(String dni) {
        Estado e = estados.computeIfAbsent(clave(dni), k -> new Estado());
        synchronized (e) {
            e.fallos++;
            if (e.fallos >= intentosAntesDeBloqueo) {
                e.bloqueosConsecutivos++;
                Duration bloqueo = bloqueoInicial.multipliedBy(1L << Math.min(e.bloqueosConsecutivos - 1, 20));
                if (bloqueo.compareTo(bloqueoMaximo) > 0) bloqueo = bloqueoMaximo;
                e.bloqueadoHasta = reloj.instant().plus(bloqueo);
                e.fallos = 0;
            }
        }
    }

    public void registrarExito(String dni) {
        estados.remove(clave(dni));
    }

    private static String clave(String dni) {
        return dni == null ? "" : dni.replaceAll("\\D", "");
    }
}
