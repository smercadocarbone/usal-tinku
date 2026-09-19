package com.tinku.reputacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Bean separado de {@link SenalesImplicitasService} a propósito: la
 * propagación transaccional de {@link #ejecutarEnSavepoint} solo tiene efecto
 * si la llamada pasa por el proxy transaccional de Spring — una auto-invocación
 * (el propio service llamándose a sí mismo) lo ignora en silencio. Ver el
 * comentario de {@code actualizarSenalesSeguro} en {@link SenalesImplicitasService}
 * para el escenario de condición de carrera que esto corrige (auditoría 2026-09-18).
 *
 * <p>{@code Propagation.REQUIRES_NEW}, no {@code NESTED}: un SAVEPOINT real
 * requiere que el {@code JpaDialect} lo soporte, y Hibernate + Spring
 * {@code JpaTransactionManager} no lo hacen por defecto — probado en este
 * mismo fix, fallaba con {@code NestedTransactionNotSupportedException} en
 * todo el suite. REQUIRES_NEW abre una transacción física independiente (su
 * propia conexión), lo que además es el comportamiento correcto acá: si
 * {@code finalizar()} hace rollback más tarde por otro motivo, esta
 * actualización de señales ya comiteada no debería revertirse con él — es
 * una señal secundaria desacoplada, no parte atómica del flujo de dinero.
 */
@Component
public class SenalesImplicitasGuard {

    private static final Logger log = LoggerFactory.getLogger(SenalesImplicitasGuard.class);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ejecutarEnSavepoint(UUID tutorId, Runnable accion) {
        try {
            accion.run();
        } catch (DataAccessException e) {
            log.warn("No se pudo actualizar señales implícitas del tutor {} (condición de "
                    + "carrera u otro error de datos, no bloqueante)", tutorId, e);
        }
    }
}
