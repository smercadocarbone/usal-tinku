package com.tinku.identidad.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * FASE2-02 / AUD-012: política mínima de contraseña — al menos 10 caracteres, con
 * al menos una letra y un número. "Distinta del DNI" se valida en el servicio
 * ({@code PoliticaPassword}), porque no todos los DTOs traen el DNI.
 */
@Documented
@Constraint(validatedBy = PasswordSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordSegura {

    String message() default "debe tener al menos 10 caracteres, con letras y números";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
