package com.tinku.identidad.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordSeguraValidator implements ConstraintValidator<PasswordSegura, String> {

    public static final int LARGO_MINIMO = 10;

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null) {
            return true; // @NotBlank se ocupa del vacío
        }
        return valor.length() >= LARGO_MINIMO
                && valor.chars().anyMatch(Character::isLetter)
                && valor.chars().anyMatch(Character::isDigit);
    }
}
