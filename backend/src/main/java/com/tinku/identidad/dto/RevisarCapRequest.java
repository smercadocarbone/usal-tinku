package com.tinku.identidad.dto;

import com.tinku.identidad.model.CategoriaAntecedenteCap;
import jakarta.validation.constraints.NotNull;

/**
 * Revisión de un CAP por el Admin de Moderación. {@code categoria} = qué antecedente
 * informa el certificado; {@code null} si no informa ninguno. La categoría manda sobre la
 * acción (BR-CAP-01/02): con un antecedente de la lista se rechaza aunque se pida aprobar, y
 * con cualquier otro queda en revisión legal. Aprobar solo prospera sin antecedentes.
 */
public record RevisarCapRequest(
        @NotNull AccionRevisionCap accion,
        CategoriaAntecedenteCap categoria
) {
}
