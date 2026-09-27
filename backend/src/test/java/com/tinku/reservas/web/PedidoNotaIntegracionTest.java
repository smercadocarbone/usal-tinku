package com.tinku.reservas.web;

import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enmienda v2.5 — Spec_M4 US-11 (pedido previo, FR-RES-027) y US-12 (nota del Tutor
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

    @Override
    int baseDnis() {
        return 41_000_000;
    }

    private ResultActions pedido(String token, UUID reservaId, String texto) throws Exception {
        return mockMvc.perform(put("/api/reservas/{id}/pedido", reservaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("texto", texto))));
    }

    // ------------------------------------------------ US-11: pedido previo (solo texto)

    @Test
    void us11_elPagadorDejaElPedido_filtrado_yElTutorLoLee() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 60);
        confirmarPago(reservaId);

        pedido(e.tokenAlumno(), reservaId, "Quiero repasar fracciones, llamame al 11 5555-4444 o a mail@x.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true));

        String texto = objectMapper.readTree(mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId)
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("texto").asText();
        assertThat(texto).contains("fracciones").doesNotContain("5555-4444").doesNotContain("mail@x.com");

        // Se puede corregir antes de la clase.
        pedido(e.tokenAlumno(), reservaId, "Mejor derivadas").andExpect(jsonPath("$.texto").value("Mejor derivadas"));
    }

    @Test
    void us11_soloElPagadorEscribe_yNadieDeAfueraLoVe() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        String tokenOtro = registrarAdulto(dniUnico(), "Otro", true, false);

        pedido(e.tokenTutor(), reservaId, "hola").andExpect(status().isForbidden());
        pedido(tokenOtro, reservaId, "hola").andExpect(status().isNotFound());
        pedido(e.tokenAlumno(), reservaId, "   ").andExpect(status().isUnprocessableEntity());
        pedido(e.tokenAlumno(), reservaId, "ver derivadas").andExpect(status().isOk());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());
    }

    @Test
    void us11_conLaReservaCanceladaYaNoSeCambia_peroSeSigueLeyendo() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);
        pedido(e.tokenAlumno(), reservaId, "ecuaciones").andExpect(status().isOk());

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId).header("Authorization", "Bearer " + e.tokenAlumno()))
                .andExpect(status().isOk());

        pedido(e.tokenAlumno(), reservaId, "otra cosa").andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/reservas/{id}/pedido", reservaId).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(jsonPath("$.texto").value("ecuaciones"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void us11_conUnMenor_loEscribeElAr_yElMenorNiLoEscribeNiLoVe() throws Exception {
        ConMenor e = arConMenor();
        UUID reservaId = reservar(e.tokenAr(), e.tutorId(), e.menorId(), a(e.fecha(), 15, 0), 30);

        pedido(e.tokenMenor(), reservaId, "hola").andExpect(status().isForbidden());
        pedido(e.tokenAr(), reservaId, "divisiones de dos cifras").andExpect(status().isOk());
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
}
