package com.tinku.aula.web;

/** ADR-M3-07 §6: lo que el cliente avisa del estado de su clasificador. Solo se registra en log. */
public enum EventoClasificador { NO_CARGA, CAIDA, REACTIVADO, CORTE_SIN_REACTIVAR }
