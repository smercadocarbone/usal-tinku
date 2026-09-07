package com.tinku.reservas.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mapea {@link EstadoReserva} a los valores en minúscula del schema `reservas` (V9). */
@Converter
public class EstadoReservaConverter implements AttributeConverter<EstadoReserva, String> {

    @Override
    public String convertToDatabaseColumn(EstadoReserva estado) {
        return estado == null ? null : estado.getValor();
    }

    @Override
    public EstadoReserva convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoReserva.parse(valor);
    }
}