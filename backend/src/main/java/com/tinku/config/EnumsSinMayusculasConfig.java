package com.tinku.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * R-B B-3 (T4-H4): un enum en la ruta o en la query ({@code ?estado=fallido}) se acepta sin
 * distinguir mayúsculas, igual que en el cuerpo ({@code spring.jackson.mapper.accept-case-insensitive-enums}).
 * Un valor que no existe sigue dando 400 con la lista de valores aceptados (ValidacionErrorHandler).
 */
@Configuration
public class EnumsSinMayusculasConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new SinMayusculas());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class SinMayusculas implements ConverterFactory<String, Enum> {
        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> tipo) {
            return valor -> {
                String limpio = valor.trim();
                if (limpio.isEmpty()) {
                    return null;
                }
                return (T) Arrays.stream(tipo.getEnumConstants())
                        .filter(c -> ((Enum) c).name().equalsIgnoreCase(limpio))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No es un valor de " + tipo.getSimpleName() + ": " + limpio));
            };
        }
    }
}
