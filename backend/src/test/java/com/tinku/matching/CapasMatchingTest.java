package com.tinku.matching;

import com.tinku.matching.web.BusquedaController;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-036.2: {@code matching} tiene las mismas capas que los otros módulos
 * ({@code model}, {@code repository}, {@code service}, {@code web}, {@code port}); ninguna clase
 * de producción queda suelta en la raíz del paquete.
 */
class CapasMatchingTest {

    @Test
    void aud036_noHayClasesSueltasEnLaRaizDeMatching() throws URISyntaxException {
        // Un web/ compilado ubica la raíz del módulo dentro de target/classes (no la de tests).
        File web = new File(BusquedaController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File raiz = new File(web, "com/tinku/matching");
        List<String> sueltas = Arrays.stream(raiz.listFiles((dir, nombre) -> nombre.endsWith(".class")))
                .map(File::getName)
                .toList();
        assertThat(sueltas).isEmpty();
        assertThat(raiz.list()).contains("model", "repository", "service", "web", "port");
    }
}
