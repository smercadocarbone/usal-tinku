package com.tinku.reservas.web;

import com.tinku.reservas.service.PedidoPrevioService;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enmienda v2.5 — Spec_M4 US-11 (pedido previo, FR-RES-027/028, ADR-M4-01) y US-12 (nota del Tutor
 * al Adulto Responsable, FR-RES-026), de punta a punta por HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class PedidoNotaIntegracionTest extends FlujosReservaBase {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired Scheduler scheduler;
    @Autowired PedidoPrevioService pedidos;

    @Override
    int baseDnis() {
        return 41_000_000;
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    private ResultActions pedido(String token, UUID reservaId, String texto, byte[] archivo, String nombre) throws Exception {
        var req = multipart(HttpMethod.PUT, "/api/reservas/{id}/pedido", reservaId);
        if (texto != null) {
            req.file(new MockMultipartFile("texto", "", MediaType.TEXT_PLAIN_VALUE, texto.getBytes()));
        }
        if (archivo != null) {
            req.file(new MockMultipartFile("archivo", nombre, MediaType.APPLICATION_OCTET_STREAM_VALUE, archivo));
        }
        return mockMvc.perform(req.header("Authorization", "Bearer " + token));
    }

    // ------------------------------------------------ US-11: pedido previo

    @Test
    void us11_elPagadorDejaElPedido_filtrado_yElTutorLoVeConSuArchivo() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 60);
        confirmarPago(reservaId);

        pedido(e.tokenAlumno(), reservaId, "Quiero repasar fracciones, llamame al 11 5555-4444 o a mail@x.com",
                PNG, "../../ejercicio 3.png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivoNombre").value("ejercicio 3.png"))
                .andExpect(jsonPath("$.archivoTipo").value("image/png"))
                .andExpect(jsonPath("$.editable").value(true));

        String texto = objectMapper.readTree(mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId)
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("texto").asText();
        assertThat(texto).contains("fracciones").doesNotContain("5555-4444").doesNotContain("mail@x.com");

        mockMvc.perform(get("/api/reservas/{id}/pedido/archivo", reservaId)
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        // Retención (ADR-M4-01): borrado agendado 24 hs después del fin de la clase.
        assertThat(scheduler.getTrigger(pedidos.triggerBorrado(reservaId)).getStartTime().toInstant())
                .isEqualTo(a(e.fecha(), 16, 0).plus(Duration.ofHours(24)));
    }

    @Test
    void us11_soloElPagadorEscribe_yNadieDeAfueraLoVe() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        String tokenOtro = registrarAdulto(dniUnico(), "Otro", true, false);

        pedido(e.tokenTutor(), reservaId, "hola", null, null).andExpect(status().isForbidden());
        pedido(tokenOtro, reservaId, "hola", null, null).andExpect(status().isNotFound());
        pedido(e.tokenAlumno(), reservaId, "ver derivadas", null, null).andExpect(status().isOk());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());
    }

    @Test
    void us11_archivoQueNoEsFotoNiPdf_422_yPedidoVacio_422() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);

        pedido(e.tokenAlumno(), reservaId, null, "<script>".getBytes(), "x.png").andExpect(status().isUnprocessableEntity());
        pedido(e.tokenAlumno(), reservaId, "   ", null, null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes028_alCancelarSeBorraElArchivoEnElActo_yElTextoQueda() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);
        pedido(e.tokenAlumno(), reservaId, "ecuaciones", PNG, "a.png").andExpect(status().isOk());

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId).header("Authorization", "Bearer " + e.tokenAlumno()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("ecuaciones"))
                .andExpect(jsonPath("$.archivoNombre").doesNotExist())
                .andExpect(jsonPath("$.editable").value(false));
        mockMvc.perform(get("/api/reservas/{id}/pedido/archivo", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isNotFound());
        assertThat(scheduler.checkExists(pedidos.triggerBorrado(reservaId))).isFalse();
    }

    @Test
    void frRes028_elJobBorraElArchivoPasadas24hsDelFin_yReprogramarMueveElBorrado() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);
        pedido(e.tokenAlumno(), reservaId, null, PNG, "a.png").andExpect(status().isOk());

        LocalDate otroDia = enDias(3);
        publicarFranja(e.tokenTutor(), otroDia, "15:00", "16:00");
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nuevoHorario", a(otroDia, 15, 0).toString()))))
                .andExpect(status().isOk());
        assertThat(scheduler.getTrigger(pedidos.triggerBorrado(reservaId)).getStartTime().toInstant())
                .isEqualTo(a(otroDia, 15, 30).plus(Duration.ofHours(24)));

        // Un disparo antes de tiempo no borra nada.
        pedidos.vencerArchivo(reservaId);
        mockMvc.perform(get("/api/reservas/{id}/pedido/archivo", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk());

        // Pasadas las 24 hs del fin (la clase queda en el pasado), el job borra.
        Instant pasado = Instant.now().minus(Duration.ofDays(2));
        jdbc.update("UPDATE reservas.reservas SET horario = ?, horario_fin = ? WHERE id = ?",
                java.sql.Timestamp.from(pasado), java.sql.Timestamp.from(pasado.plus(Duration.ofMinutes(30))), reservaId);
        pedidos.vencerArchivo(reservaId);
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isNoContent());
    }

    @Test
    void us11_conUnMenor_loEscribeElAr_yElMenorNiLoEscribeNiLoVe() throws Exception {
        ConMenor e = arConMenor();
        UUID reservaId = reservar(e.tokenAr(), e.tutorId(), e.menorId(), a(e.fecha(), 15, 0), 30);

        pedido(e.tokenMenor(), reservaId, "hola", null, null).andExpect(status().isForbidden());
        pedido(e.tokenAr(), reservaId, "divisiones de dos cifras", null, null).andExpect(status().isOk());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenMenor()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(jsonPath("$.texto").value("divisiones de dos cifras"));
    }

    // ------------------------------------------------ US-12: nota del Tutor al AR

    private ResultActions nota(String token, UUID reservaId, String texto) throws Exception {
        return mockMvc.perform(put("/api/reservas/{id}/nota", reservaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("texto", texto))));
    }

    private void finalizar(UUID reservaId) {
        jdbc.update("UPDATE reservas.reservas SET estado = 'finalizada' WHERE id = ?", reservaId);
    }

    @Test
    void us12_elTutorDejaLaNota_alArLeLlegaElAviso_yElMenorNoLaVe() throws Exception {
        ConMenor e = arConMenor();
        UUID reservaId = reservar(e.tokenAr(), e.tutorId(), e.menorId(), a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);

        nota(e.tokenTutor(), reservaId, "Vimos fracciones").andExpect(status().isUnprocessableEntity()); // sin terminar
        finalizar(reservaId);
        nota(e.tokenAr(), reservaId, "yo").andExpect(status().isForbidden());
        nota(e.tokenTutor(), reservaId, "Vimos fracciones. Le cuesta simplificar; conviene practicar. Mi cel 1155554444")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true));

        assertThat(avisosDe(e.arId())).contains(TipoNotificacion.NOTA_CLASE);
        String texto = objectMapper.readTree(mockMvc.perform(get("/api/reservas/{id}/nota", reservaId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andReturn().getResponse().getContentAsString()).get("texto").asText();
        assertThat(texto).contains("fracciones").doesNotContain("1155554444");
        mockMvc.perform(get("/api/reservas/{id}/nota", reservaId).header("Authorization", "Bearer " + e.tokenMenor()))
                .andExpect(status().isNotFound());

        // Corregirla dentro de las 48 hs no avisa de nuevo; después queda fija.
        nota(e.tokenTutor(), reservaId, "Vimos fracciones y decimales").andExpect(status().isOk());
        assertThat(avisosDe(e.arId()).stream().filter(t -> t == TipoNotificacion.NOTA_CLASE)).hasSize(1);
        jdbc.update("UPDATE reservas.notas_clase SET created_at = now() - interval '49 hours' WHERE reserva_id = ?", reservaId);
        nota(e.tokenTutor(), reservaId, "otra").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void us12_enUnaClaseEntreAdultosNoHayNota() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);
        finalizar(reservaId);

        nota(e.tokenTutor(), reservaId, "Bien").andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/reservas/{id}/nota", reservaId).header("Authorization", "Bearer " + e.tokenAlumno()))
                .andExpect(status().isNoContent());
    }

    @Test
    void quitarArchivo_soloElPagador() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        pedido(e.tokenAlumno(), reservaId, "texto", PNG, "a.png").andExpect(status().isOk());

        mockMvc.perform(delete("/api/reservas/{id}/pedido/archivo", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reservas/{id}/pedido/archivo", reservaId).header("Authorization", "Bearer " + e.tokenAlumno()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenAlumno()))
                .andExpect(jsonPath("$.archivoNombre").doesNotExist())
                .andExpect(jsonPath("$.texto").value("texto"));
    }
}
