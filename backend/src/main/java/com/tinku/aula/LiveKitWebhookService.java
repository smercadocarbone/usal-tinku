package com.tinku.aula;

import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Registro de joins de participantes del webhook de LiveKit (T-M3-02). Quién
 * es "tutor" y quién "estudiante" NO viaja en el payload del webhook: se
 * resuelve contra la Reserva de la sesión → el beneficiario entra como
 * estudiante, el tutor como tutor (identidad = id o dni del usuario).
 *
 * El job de no-show T+10 (T-M3-04) consulta {@code estudiante_joined_at} /
 * {@code tutor_joined_at} para decidir el evento «sin join» — por eso estos
 * timestamps se graban aquí y nunca en memoria.
 */
@Service
public class LiveKitWebhookService {

    private final SesionAprendizajeRepository sesionRepo;
    private final ReservaRepository reservaRepo;
    private final SesionService sesionService;

    public LiveKitWebhookService(SesionAprendizajeRepository sesionRepo,
                                 ReservaRepository reservaRepo,
                                 SesionService sesionService) {
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
        this.sesionService = sesionService;
    }

    @Transactional
    public void registrarJoin(String livekitRoomId, String identity) {
        if (livekitRoomId == null || identity == null || identity.isBlank()) {
            return;
        }
        SesionAprendizaje sesion = sesionRepo.findByLivekitRoomId(livekitRoomId).orElse(null);
        if (sesion == null) {
            return; // sala no registrada todavía (o de otro entorno): no es nuestro join
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElse(null);
        if (reserva == null) {
            return;
        }
        boolean nuevoJoin = false;
        boolean rolMatched = false;
        if (mismaPersona(reserva.getTutor(), identity)) {
            rolMatched = true;
            nuevoJoin = sesion.getTutorJoinedAt() == null;
            if (nuevoJoin) {
                sesion.setTutorJoinedAt(Instant.now());
            }
            sesion.setTutorConectado(true); // FASE2-05 (AUD-029)
        } else if (mismaPersona(reserva.getBeneficiario(), identity)) {
            rolMatched = true;
            nuevoJoin = sesion.getEstudianteJoinedAt() == null;
            if (nuevoJoin) {
                sesion.setEstudianteJoinedAt(Instant.now());
            }
            sesion.setEstudianteConectado(true); // FASE2-05 (AUD-029)
        }
        if (!rolMatched) {
            return; // tercero: no forma el par de la clase (FASE2-05 §4.3)
        }
        if (sesion.isTutorConectado() && sesion.isEstudianteConectado()) {
            // El par volvió a estar completo: la desconexión anterior deja de
            // contar como fin efectivo (reconexión, Spec fase2-05 §4.3).
            sesion.setParRotoAt(null);
        }
        if (nuevoJoin && SesionAprendizaje.ESTADO_NO_INICIADA.equals(sesion.getEstado())) {
            // Primer join real: la sesión arranca (US-2) — inicio_real alimenta el
            // cálculo de duración efectiva al finalizar (US-5/M6).
            sesion.setEstado(SesionAprendizaje.ESTADO_EN_CURSO);
            sesion.setInicioReal(Instant.now());
        }
        sesionRepo.save(sesion);
        if (sesion.getTutorJoinedAt() != null && sesion.getEstudianteJoinedAt() != null) {
            // Plan M3 §3.2 punto 4: el no-show ya no tiene sentido — se cancela
            // el job T+10, NO se deja correr y descartar su resultado.
            sesionService.cancelarNoShow(sesion.getId());
        }
    }

    /**
     * FASE2-05 (AUD-029) — {@code participant_left}: marca el flag del rol en
     * {@code false} y, si antes estaban los dos conectados, fija {@code par_roto_at}
     * en el instante del primer abandono (el "fin efectivo" para el corte). Un
     * tercero no cuenta y una sesión ya cortada no se toca.
     */
    @Transactional
    public void registrarSalida(String livekitRoomId, String identity) {
        if (livekitRoomId == null || identity == null || identity.isBlank()) {
            return;
        }
        SesionAprendizaje sesion = sesionRepo.findByLivekitRoomId(livekitRoomId).orElse(null);
        if (sesion == null) {
            return;
        }
        if (yaCerrada(sesion)) {
            return; // left tardío del corte (kill-switch o automático): no toca nada
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElse(null);
        if (reserva == null) {
            return;
        }
        boolean ambosConectados = sesion.isTutorConectado() && sesion.isEstudianteConectado();
        if (mismaPersona(reserva.getTutor(), identity)) {
            sesion.setTutorConectado(false);
        } else if (mismaPersona(reserva.getBeneficiario(), identity)) {
            sesion.setEstudianteConectado(false);
        } else {
            return; // tercero: no forma el par
        }
        if (ambosConectados) {
            sesion.setParRotoAt(Instant.now());
        }
        sesionRepo.save(sesion);
    }

    /**
     * FASE2-05 (AUD-029) — {@code room_finished}: la sala terminó, así que quedan
     * los dos desconectados. Si estaban los dos conectados, {@code par_roto_at}
     * queda en este instante. No-op si la sesión ya cerró.
     */
    @Transactional
    public void registrarSalaTerminada(String livekitRoomId) {
        if (livekitRoomId == null) {
            return;
        }
        SesionAprendizaje sesion = sesionRepo.findByLivekitRoomId(livekitRoomId).orElse(null);
        if (sesion == null || yaCerrada(sesion)) {
            return;
        }
        boolean ambosConectados = sesion.isTutorConectado() && sesion.isEstudianteConectado();
        sesion.setTutorConectado(false);
        sesion.setEstudianteConectado(false);
        if (ambosConectados) {
            sesion.setParRotoAt(Instant.now());
        }
        sesionRepo.save(sesion);
    }

    private boolean yaCerrada(SesionAprendizaje sesion) {
        return SesionAprendizaje.ESTADO_FINALIZADA.equals(sesion.getEstado())
                || SesionAprendizaje.ESTADO_FINALIZADA_ANTICIPADA.equals(sesion.getEstado())
                || SesionAprendizaje.ESTADO_INTERRUMPIDA.equals(sesion.getEstado());
    }

    // La rama del DNI es compatibilidad con tokens emitidos antes de AUD-003 (identity = DNI).
    // Se borra en FASE 3 (plan de remediación, Task 3.12). No agregar usos nuevos.
    private boolean mismaPersona(Usuario usuario, String identity) {
        return usuario.getId().toString().equals(identity) || usuario.getDni().equals(identity);
    }
}