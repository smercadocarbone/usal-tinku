package com.tinku.evaluacion;

import com.tinku.identidad.model.Usuario;
import com.tinku.matching.port.MatchingServiceClient.ResultadoMatch;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-TES-11b (docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md §4 y §6): instrumentación
 * mínima de las sesiones moderadas, con {@code tinku.evaluacion.habilitada=true}.
 *
 * <p>La base de Testcontainers es compartida con el resto de la suite, así que los indicadores se
 * verifican por diferencia (antes/después), no por valor absoluto.</p>
 */
@TestPropertySource(properties = "tinku.evaluacion.habilitada=true")
class EvaluacionIntegracionTest extends IntegracionBase {

    @Autowired ReservaRepository reservaRepo;

    /** Cada clase en un horario distinto: un Tutor no puede tener dos reservas superpuestas. */
    private static final java.util.concurrent.atomic.AtomicInteger HORAS_ATRAS = new java.util.concurrent.atomic.AtomicInteger(2);

    // ---- helpers ----

    private MvcResult buscar(String token, String texto) throws Exception {
        return mockMvc.perform(post("/api/busquedas").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto_busqueda", texto))))
                .andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions aceptar(String token, String busquedaId,
                                                                      UUID tutorId, String tipo) throws Exception {
        return mockMvc.perform(post("/api/busquedas/" + busquedaId + "/aceptaciones")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("tutorId", tutorId.toString(), "tipo", tipo))));
    }

    private JsonNode indicadores(String tokenAdmin) throws Exception {
        return json(mockMvc.perform(get("/api/admin/evaluacion/indicadores").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk()).andReturn());
    }

    private String tokenAdmin() throws Exception {
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");
        return token(admin);
    }

    /** Clase entre {@code alumno} y {@code tutor} con la sesión en {@code estadoSesion}; devuelve el id de la sesión. */
    private UUID clase(Usuario alumno, Usuario tutor, String estadoSesion) {
        Reserva reserva = new Reserva();
        reserva.setPagador(alumno);
        reserva.setBeneficiario(alumno);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(3600L * HORAS_ATRAS.getAndIncrement()));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepo.saveAndFlush(reserva);
        return jdbc.queryForObject("INSERT INTO aula.sesiones_aprendizaje (reserva_id, estado) VALUES (?, ?) RETURNING id",
                UUID.class, reserva.getId(), estadoSesion);
    }

    private void calificar(UUID sesionId, Usuario autor, String direccion, int estrellas) {
        jdbc.update("""
                INSERT INTO reputacion.calificaciones (sesion_id, autor_id, direccion, estrellas, editable_hasta)
                VALUES (?, ?, ?, ?, now() + interval '48 hours')""", sesionId, autor.getId(), direccion, estrellas);
    }

    private ResultadoNps pendiente(String token) throws Exception {
        JsonNode r = json(mockMvc.perform(get("/api/evaluacion/nps/pendiente").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        return new ResultadoNps(r.get("ofrecer").asBoolean());
    }

    private record ResultadoNps(boolean ofrecer) {
    }

    private org.springframework.test.web.servlet.ResultActions responderNps(String token, Object puntaje) throws Exception {
        return mockMvc.perform(post("/api/evaluacion/nps").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"puntaje\":" + puntaje + "}"));
    }

    // ---- 4.1 búsquedas ----

    @Test
    void busqueda_conFlag_registraResultadosEnOrden_sinTexto() throws Exception {
        Usuario a = tutor(null, null);
        Usuario b = tutor(null, null);
        String texto = "series de Taylor y derivadas";
        when(matchingClient.match(anyList(), eq(texto)))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9), new ResultadoMatch(b.getId(), 0.8)));
        Usuario yo = adulto(false);

        MvcResult res = buscar(token(yo), texto);

        String busquedaId = res.getResponse().getHeader("X-Busqueda-Id");
        assertThat(busquedaId).isNotBlank();
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT usuario_id, longitud_texto, cantidad_resultados, array_to_string(tutor_ids, ',') AS ids "
                        + "FROM matching.evaluacion_busquedas WHERE id = ?::uuid", busquedaId);
        assertThat(fila.get("usuario_id")).isEqualTo(yo.getId());
        assertThat(fila.get("longitud_texto")).isEqualTo(texto.length());
        assertThat(fila.get("cantidad_resultados")).isEqualTo(2);
        assertThat(fila.get("ids")).isEqualTo(a.getId() + "," + b.getId());
        // Art. V: el texto libre no se guarda en ninguna columna.
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'matching' AND table_name = 'evaluacion_busquedas'""", String.class);
        assertThat(columnas).noneMatch(c -> c.contains("texto") && !c.equals("longitud_texto"));
    }

    @Test
    void busquedaDeMenor_noSeRegistra() throws Exception {
        Usuario menor = menor(adulto(true));
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM matching.evaluacion_busquedas", Integer.class);

        MvcResult res = buscar(token(menor), "fracciones equivalentes");

        assertThat(res.getResponse().getHeader("X-Busqueda-Id")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matching.evaluacion_busquedas", Integer.class))
                .isEqualTo(antes);
    }

    @Test
    void aceptacion_calculaPosicionEnElServidor_esIdempotente() throws Exception {
        Usuario a = tutor(null, null);
        Usuario b = tutor(null, null);
        String texto = "teoremas de geometría euclidiana";
        when(matchingClient.match(anyList(), eq(texto)))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9), new ResultadoMatch(b.getId(), 0.85)));
        String t = token(adulto(false));
        String busquedaId = buscar(t, texto).getResponse().getHeader("X-Busqueda-Id");

        aceptar(t, busquedaId, b.getId(), "PERFIL").andExpect(status().isNoContent());
        aceptar(t, busquedaId, b.getId(), "PERFIL").andExpect(status().isNoContent());
        aceptar(t, busquedaId, b.getId(), "RESERVA").andExpect(status().isNoContent());

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT tutor_id, posicion, tipo FROM matching.evaluacion_aceptaciones WHERE busqueda_id = ?::uuid ORDER BY tipo",
                busquedaId);
        assertThat(filas).hasSize(2);
        assertThat(filas).allSatisfy(f -> {
            assertThat(f.get("tutor_id")).isEqualTo(b.getId());
            assertThat(f.get("posicion")).isEqualTo(2);
        });
        assertThat(filas).extracting(f -> f.get("tipo")).containsExactly("PERFIL", "RESERVA");
    }

    @Test
    void aceptacion_tutorFueraDeResultados_422() throws Exception {
        Usuario a = tutor(null, null);
        Usuario otro = tutor(null, null);
        String texto = "probabilidad condicional";
        when(matchingClient.match(anyList(), eq(texto))).thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9)));
        String t = token(adulto(false));
        String busquedaId = buscar(t, texto).getResponse().getHeader("X-Busqueda-Id");

        aceptar(t, busquedaId, otro.getId(), "PERFIL").andExpect(status().isUnprocessableEntity());
        aceptar(t, busquedaId, a.getId(), "OTRA_COSA").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aceptacion_busquedaAjenaOInexistente_404() throws Exception {
        Usuario a = tutor(null, null);
        String texto = "estequiometría y moles";
        when(matchingClient.match(anyList(), eq(texto))).thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9)));
        String busquedaId = buscar(token(adulto(false)), texto).getResponse().getHeader("X-Busqueda-Id");

        String otro = token(adulto(false));
        aceptar(otro, busquedaId, a.getId(), "PERFIL").andExpect(status().isNotFound());
        aceptar(otro, UUID.randomUUID().toString(), a.getId(), "PERFIL").andExpect(status().isNotFound());
    }

    // ---- 4.2 NPS ----

    @Test
    void nps_noSeOfreceSinClase_seOfreceConClaseFinalizada() throws Exception {
        Usuario alumno = adulto(false);
        Usuario tutor = tutor(null, null);
        String tAlumno = token(alumno);
        String tTutor = token(tutor);
        assertThat(pendiente(tAlumno).ofrecer()).isFalse();
        responderNps(tAlumno, 9).andExpect(status().isForbidden());

        clase(alumno, tutor, "en_curso");
        assertThat(pendiente(tAlumno).ofrecer()).as("clase sin terminar").isFalse();

        clase(alumno, tutor, "finalizada");
        assertThat(pendiente(tAlumno).ofrecer()).isTrue();
        assertThat(pendiente(tTutor).ofrecer()).as("el Tutor de la clase también").isTrue();
    }

    @Test
    void nps_seOfreceDespuesDeCalificar() throws Exception {
        Usuario alumno = adulto(false);
        Usuario tutor = tutor(null, null);
        UUID sesion = clase(alumno, tutor, "interrumpida");
        String t = token(alumno);
        assertThat(pendiente(t).ofrecer()).isFalse();

        calificar(sesion, alumno, "estudiante_a_tutor", 5);

        assertThat(pendiente(t).ofrecer()).isTrue();
    }

    @Test
    void nps_unaRespuestaPorUsuario_409AlRepetir() throws Exception {
        Usuario alumno = adulto(false);
        clase(alumno, tutor(null, null), "finalizada");
        String t = token(alumno);

        responderNps(t, 9).andExpect(status().isCreated());
        responderNps(t, 3).andExpect(status().isConflict());

        assertThat(pendiente(t).ofrecer()).isFalse();
        assertThat(jdbc.queryForObject("SELECT puntaje FROM admin.nps_respuestas WHERE usuario_id = ?",
                Integer.class, alumno.getId())).isEqualTo(9);
    }

    @Test
    void nps_puntajeFueraDeRango_422() throws Exception {
        Usuario alumno = adulto(false);
        clase(alumno, tutor(null, null), "finalizada");
        String t = token(alumno);

        responderNps(t, 11).andExpect(status().isUnprocessableEntity());
        responderNps(t, -1).andExpect(status().isUnprocessableEntity());
        responderNps(t, "null").andExpect(status().isUnprocessableEntity());
        responderNps(t, 0).andExpect(status().isCreated());
    }

    @Test
    void nps_menor_nuncaSeOfrece_403() throws Exception {
        Usuario ar = adulto(true);
        Usuario menor = menor(ar);
        UUID sesion = clase(menor, tutor(null, null), "finalizada");
        calificar(sesion, menor, "estudiante_a_tutor", 5);
        String t = token(menor);

        assertThat(pendiente(t).ofrecer()).isFalse();
        responderNps(t, 10).andExpect(status().isForbidden());
    }

    // ---- 4.3 panel ----

    @Test
    void indicadores_calculanI01NpsI02_comoElCalculoManual() throws Exception {
        String admin = tokenAdmin();
        JsonNode antes = indicadores(admin);

        // 3 búsquedas: 2 con resultado (una aceptada en posición 1 y 2), 1 sin resultado.
        Usuario a = tutor(null, null);
        Usuario b = tutor(null, null);
        when(matchingClient.match(anyList(), eq("álgebra lineal matrices")))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9), new ResultadoMatch(b.getId(), 0.85)));
        when(matchingClient.match(anyList(), eq("cálculo vectorial")))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9)));
        when(matchingClient.match(anyList(), eq("clases de tango bailarín")))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.2)));
        Usuario yo = adulto(false);
        String t = token(yo);
        String b1 = buscar(t, "álgebra lineal matrices").getResponse().getHeader("X-Busqueda-Id");
        buscar(t, "cálculo vectorial");
        buscar(t, "clases de tango bailarín");
        aceptar(t, b1, a.getId(), "PERFIL").andExpect(status().isNoContent());
        aceptar(t, b1, b.getId(), "RESERVA").andExpect(status().isNoContent());

        // NPS: un promotor (10) y un detractor (4).
        Usuario p = adulto(false);
        Usuario d = adulto(false);
        clase(p, a, "finalizada");
        clase(d, a, "finalizada");
        responderNps(token(p), 10).andExpect(status().isCreated());
        responderNps(token(d), 4).andExpect(status().isCreated());

        // I-02: dos calificaciones al Tutor (5 y 3); la del Tutor al alumno no cuenta.
        UUID s = clase(yo, b, "finalizada");
        UUID s2 = clase(p, b, "finalizada");
        calificar(s, yo, "estudiante_a_tutor", 5);
        calificar(s2, p, "estudiante_a_tutor", 3);
        calificar(s, b, "tutor_a_estudiante", 1);

        JsonNode despues = indicadores(admin);

        assertThat(despues.get("habilitada").asBoolean()).isTrue();
        assertThat(despues.get("topK").asInt()).isEqualTo(5);
        assertThat(delta(antes, despues, "busquedas", "total")).isEqualTo(3);
        assertThat(delta(antes, despues, "busquedas", "conResultado")).isEqualTo(2);
        assertThat(delta(antes, despues, "busquedas", "sinResultado")).isEqualTo(1);
        assertThat(delta(antes, despues, "i01", "busquedasConAceptacion")).isEqualTo(1);
        assertThat(posicion(despues, 1) - posicion(antes, 1)).isEqualTo(1);
        assertThat(posicion(despues, 2) - posicion(antes, 2)).isEqualTo(1);
        assertThat(delta(antes, despues, "i01", "aceptacionesPerfil")).isEqualTo(1);
        assertThat(delta(antes, despues, "i01", "aceptacionesReserva")).isEqualTo(1);
        double tasa = despues.get("i01").get("busquedasConAceptacion").asDouble()
                / despues.get("busquedas").get("conResultado").asDouble();
        assertThat(despues.get("i01").get("tasa").asDouble()).isCloseTo(tasa, within(1e-9));

        assertThat(delta(antes, despues, "nps", "respuestas")).isEqualTo(2);
        assertThat(delta(antes, despues, "nps", "promotores")).isEqualTo(1);
        assertThat(delta(antes, despues, "nps", "detractores")).isEqualTo(1);
        JsonNode nps = despues.get("nps");
        double valor = 100.0 * (nps.get("promotores").asDouble() - nps.get("detractores").asDouble())
                / nps.get("respuestas").asDouble();
        assertThat(nps.get("valor").asDouble()).isCloseTo(valor, within(1e-9));

        assertThat(delta(antes, despues, "i02", "calificaciones")).isEqualTo(2);
        double sumaAntes = antes.get("i02").get("promedio").isNull() ? 0
                : antes.get("i02").get("promedio").asDouble() * antes.get("i02").get("calificaciones").asInt();
        double sumaDespues = despues.get("i02").get("promedio").asDouble() * despues.get("i02").get("calificaciones").asInt();
        assertThat(sumaDespues - sumaAntes).isCloseTo(8.0, within(1e-6));
    }

    @Test
    void indicadores_filtranPorPeriodo() throws Exception {
        String admin = tokenAdmin();
        JsonNode futuro = json(mockMvc.perform(get("/api/admin/evaluacion/indicadores?desde=2999-01-01&hasta=2999-12-31")
                .header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn());
        assertThat(futuro.get("busquedas").get("total").asInt()).isZero();
        assertThat(futuro.get("i01").get("tasa").isNull()).isTrue();
        assertThat(futuro.get("nps").get("valor").isNull()).isTrue();
        assertThat(futuro.get("i02").get("promedio").isNull()).isTrue();
    }

    @Test
    void indicadores_soloAdmin_403ParaOtros() throws Exception {
        mockMvc.perform(get("/api/admin/evaluacion/indicadores").header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/evaluacion/indicadores").header("Authorization", bearer(token(tutor(null, null)))))
                .andExpect(status().isForbidden());
    }

    private static int delta(JsonNode antes, JsonNode despues, String grupo, String campo) {
        return despues.get(grupo).get(campo).asInt() - antes.get(grupo).get(campo).asInt();
    }

    private static int posicion(JsonNode r, int pos) {
        for (JsonNode n : r.get("i01").get("porPosicion")) {
            if (n.get("posicion").asInt() == pos) {
                return n.get("cantidad").asInt();
            }
        }
        return 0;
    }
}
