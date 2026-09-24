package com.tinku.admin.notificacion;

import com.tinku.shared.notificacion.TipoNotificacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fila del outbox / bandeja in-app (V32, FASE2-03). */
@Entity
@Table(name = "notificaciones", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "destinatario_id", nullable = false, updatable = false)
    private UUID destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private TipoNotificacion tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> datos = new HashMap<>();

    @Column(name = "creada_at", nullable = false, updatable = false)
    private Instant creadaAt = Instant.now();

    @Column(name = "leida_at")
    private Instant leidaAt;

    @Column(name = "enviada_email_at")
    private Instant enviadaEmailAt;

    @Column(name = "intentos_email", nullable = false)
    private int intentosEmail;

    @Column(name = "proximo_intento_email_at")
    private Instant proximoIntentoEmailAt;

    @Column(name = "email_descartado_at")
    private Instant emailDescartadoAt;
}
