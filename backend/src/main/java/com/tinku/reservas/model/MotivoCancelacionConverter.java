package com.tinku.reservas.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mapea {@link MotivoCancelacion} a los valores en minúscula del schema `reservas` (V9). */
@Converter
public class MotivoCancelacionConverter implements AttributeConverter<MotivoCancelacion, String> {

    @Override
    public String convertToDatabaseColumn(MotivoCancelacion motivo) {
        return motivo == null ? null : motivo.getValor();
    }

    @Override
    public MotivoCancelacion convertToEntityAttribute(String valor) {
        return valor == null ? null : MotivoCancelacion.parse(valor);
    }
}