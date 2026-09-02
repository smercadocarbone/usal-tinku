package com.tinku;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del monolito modular de Tinku.
 * Ver Constitucion_Tinku.md, Articulo VIII: un solo proceso, nueve modulos internos
 * con limites de dominio claros, ninguno de ellos un microservicio propio salvo
 * el Motor de Matching (proceso Python separado, ver tinku-matching-service/).
 */
@SpringBootApplication
public class TinkuApplication {
    public static void main(String[] args) {
        SpringApplication.run(TinkuApplication.class, args);
    }
}
