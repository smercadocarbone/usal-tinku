package com.tinku.reservas.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mapea {@link EstadoSolicitud} a los valores en minúscula del schema `reservas` (V9). */
@Converter
public class EstadoSolicitudConverter implements AttributeConverter<EstadoSolicitud, String> {

    @Override
    public String convertToDatabaseColumn(EstadoSolicitud estado) {
        return estado == null ? null : estado.getValor();
    }

    @Override
    public EstadoSolicitud convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoSolicitud.parse(valor);
    }
}