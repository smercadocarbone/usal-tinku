package com.tinku.pagos.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mapea {@link EstadoTransaccion} a los valores en minúscula del schema `pagos` (V11). */
@Converter
public class EstadoTransaccionConverter implements AttributeConverter<EstadoTransaccion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoTransaccion estado) {
        return estado == null ? null : estado.getValor();
    }

    @Override
    public EstadoTransaccion convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoTransaccion.parse(valor);
    }
}