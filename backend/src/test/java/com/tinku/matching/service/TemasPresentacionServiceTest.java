package com.tinku.matching.service;

import com.tinku.matching.port.MatchingServiceClient;
import com.tinku.matching.port.MatchingServiceClient.SugerenciaTema;
import com.tinku.matching.repository.PerfilTutorTemasRepository;
import com.tinku.matching.repository.PerfilTutorTemasRepository.TemaCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test de {@link TemasPresentacionService} (FR-MATCH-015): solo vuelven los temas del
 * catálogo sobre el umbral de la búsqueda; si ninguno llega, la consulta queda fuera del espacio
 * y solo se informa el mejor puntaje.
 */
class TemasPresentacionServiceTest {

    private static final UUID ALGEBRA = UUID.randomUUID();
    private static final UUID FUNCIONES = UUID.randomUUID();

    private MatchingServiceClient client;
    private PerfilTutorTemasRepository repo;
    private TemasPresentacionService service;

    @BeforeEach
    void setUp() {
        client = mock(MatchingServiceClient.class);
        repo = mock(PerfilTutorTemasRepository.class);
        when(repo.contarTemasConEmbedding()).thenReturn(1310);
        service = new TemasPresentacionService(client, repo, 0.46);
    }

    @Test
    void devuelveSoloLosTemasSobreElUmbralEnOrdenDeSimilitud() {
        when(client.temasCercanos(eq("me cuesta despejar la x"), anyInt())).thenReturn(List.of(
                new SugerenciaTema(ALGEBRA.toString(), 0.71),
                new SugerenciaTema(FUNCIONES.toString(), 0.52),
                new SugerenciaTema(UUID.randomUUID().toString(), 0.31)));
        when(repo.temasDelCatalogo(any())).thenReturn(List.of(
                new TemaCatalogo(FUNCIONES, "Funciones", "Matemática", "secundario"),
                new TemaCatalogo(ALGEBRA, "Ecuaciones de primer grado", "Matemática", "secundario")));

        TemasPresentacionService.Resultado r = service.buscar("  me cuesta despejar la x ");

        assertThat(r.temas()).extracting(TemasPresentacionService.Tema::nombre)
                .containsExactly("Ecuaciones de primer grado", "Funciones");
        assertThat(r.mejorScore()).isEqualTo(0.71);
        assertThat(r.umbral()).isEqualTo(0.46);
        assertThat(r.temasEnCatalogo()).isEqualTo(1310);
        verify(repo).temasDelCatalogo(List.of(ALGEBRA, FUNCIONES));
    }

    @Test
    void sinNingunTemaSobreElUmbralQuedaFueraDelEspacio() {
        when(client.temasCercanos(anyString(), anyInt())).thenReturn(List.of(
                new SugerenciaTema(UUID.randomUUID().toString(), 0.33)));
        when(repo.temasDelCatalogo(List.of())).thenReturn(List.of());

        TemasPresentacionService.Resultado r = service.buscar("clases de tango");

        assertThat(r.temas()).isEmpty();
        assertThat(r.mejorScore()).isEqualTo(0.33);
    }

    @Test
    void textoVacioNoLlamaAlMatching() {
        TemasPresentacionService.Resultado r = service.buscar("   ");

        assertThat(r.temas()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void elTextoSeRecortaAlMaximo() {
        when(client.temasCercanos(anyString(), anyInt())).thenReturn(List.of());
        when(repo.temasDelCatalogo(List.of())).thenReturn(List.of());

        service.buscar("a".repeat(500));

        verify(client).temasCercanos("a".repeat(TemasPresentacionService.MAX_TEXTO), TemasPresentacionService.TEMAS_CONSULTADOS);
    }
}
