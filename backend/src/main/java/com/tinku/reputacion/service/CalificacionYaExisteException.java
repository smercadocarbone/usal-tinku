package com.tinku.reputacion.service;

public class CalificacionYaExisteException extends RuntimeException {
    public CalificacionYaExisteException() {
        super("Ya existe una calificacion de este autor para esta Sesion.");
    }
}