package com.tinku.pagos.service;

import com.tinku.pagos.model.EstadoPasarela;
import com.tinku.pagos.repository.PasarelaEstadoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FASE2-07 / AUD-018 (P2 = opción a): el Modo Bypass solo se puede activar fuera
 * de {@code prod}. Test unitario con {@link MockEnvironment}: un contexto completo
 * con {@code prod} no levanta (ArranqueSeguroValidator aborta con el OCR stub).
 */
class PasarelaServiceBypassTest {

    private final PasarelaEstadoRepository repo = mock(PasarelaEstadoRepository.class);

    private PasarelaService servicio(String... perfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfiles);
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        return new PasarelaService(repo, env);
    }

    @Test
    void apagarPasarela_conPerfilProd_409() {
        PasarelaService s = servicio("prod");
        assertThatThrownBy(() -> s.establecerHabilitada(false, UUID.randomUUID()))
                .isInstanceOf(BypassNoPermitidoException.class);
        verify(repo, never()).save(any(EstadoPasarela.class));
        assertThat(s.bypassPermitido()).isFalse();
    }

    @Test
    void apagarPasarela_fueraDeProd_permitido() {
        PasarelaService s = servicio("dev");
        assertThat(s.establecerHabilitada(false, UUID.randomUUID())).isFalse();
        assertThat(s.bypassPermitido()).isTrue();
    }

    @Test
    void reactivarPasarela_conPerfilProd_permitido() {
        PasarelaService s = servicio("prod");
        assertThat(s.establecerHabilitada(true, UUID.randomUUID())).isTrue();
    }
}
