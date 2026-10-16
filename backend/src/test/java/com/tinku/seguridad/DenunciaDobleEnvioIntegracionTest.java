package com.tinku.seguridad;

import com.tinku.identidad.model.Usuario;
import com.tinku.seguridad.model.MotivoDenuncia;
import com.tinku.seguridad.service.DenunciaService;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Doble envío de "Denunciar": dos POST iguales seguidos creaban dos denuncias en revisión del
 * mismo caso (dos entradas en la cola de Moderación y, si se resolvían las dos, dos sanciones).
 */
class DenunciaDobleEnvioIntegracionTest extends IntegracionBase {

    @Autowired DenunciaService denunciaService;

    @Test
    void denunciarDosVecesLoMismo_laSegundaSeRechazaMientrasLaPrimeraEstaEnRevision() {
        Usuario denunciante = adulto(false);
        Usuario denunciado = adulto(false);

        denunciaService.presentar(denunciante, denunciado.getId(), null, MotivoDenuncia.FRAUDE, null);
        assertThatThrownBy(() -> denunciaService.presentar(
                denunciante, denunciado.getId(), null, MotivoDenuncia.FRAUDE, null))
                .isInstanceOf(DenunciaDuplicadaException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM seguridad.denuncias WHERE denunciante_id = ? "
                + "AND denunciado_id = ?", Integer.class, denunciante.getId(), denunciado.getId())).isEqualTo(1);
    }
}
