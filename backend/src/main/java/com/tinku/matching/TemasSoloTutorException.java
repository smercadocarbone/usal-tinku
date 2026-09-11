package com.tinku.matching;

/** PUT /api/tutores/me/temas: solo el perfil TUTOR define sus temas de
 * matching (Artículo II — un menor nunca configura qué se matchea para él). */
public class TemasSoloTutorException extends RuntimeException {

    public TemasSoloTutorException() {
        super("Solo el perfil Tutor puede definir sus temas de matching.");
    }
}