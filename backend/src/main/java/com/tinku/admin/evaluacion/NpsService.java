package com.tinku.admin.evaluacion;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.evaluacion.EvaluacionFlag;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-TES-11b §4.2 (I-04): NPS de intención, una vez por usuario, ofrecido después de su primera
 * clase finalizada (como alumno, pagador o Tutor) o de su primera calificación. Nunca a un Menor
 * (Art. II). Reemplaza a PT8 ("al cierre del piloto"), que ya no existe.
 *
 * <p>Lee {@code aula}, {@code reservas} y {@code reputacion} solo para decidir si ofrecerlo (lectura,
 * sin reglas de negocio de esos módulos).</p>
 */
@Service
public class NpsService {

    static final int PUNTAJE_MAXIMO = 10;

    private final JdbcTemplate jdbc;
    private final EvaluacionFlag flag;

    public NpsService(JdbcTemplate jdbc, EvaluacionFlag flag) {
        this.jdbc = jdbc;
        this.flag = flag;
    }

    public boolean ofrecer(Usuario usuario) {
        flag.exigir();
        return usuario.getTipo() != TipoUsuario.MENOR && !respondio(usuario) && tuvoClase(usuario);
    }

    @Transactional
    public void responder(Usuario usuario, Integer puntaje) {
        flag.exigir();
        if (usuario.getTipo() == TipoUsuario.MENOR) {
            throw new NpsNoDisponibleException();
        }
        if (puntaje == null || puntaje < 0 || puntaje > PUNTAJE_MAXIMO) {
            throw new NpsPuntajeInvalidoException();
        }
        if (respondio(usuario)) {
            throw new NpsYaRespondidoException();
        }
        if (!tuvoClase(usuario)) {
            throw new NpsNoDisponibleException();
        }
        try {
            jdbc.update("INSERT INTO admin.nps_respuestas (usuario_id, puntaje) VALUES (?, ?)",
                    usuario.getId(), puntaje.shortValue());
        } catch (DuplicateKeyException e) {
            throw new NpsYaRespondidoException();
        }
    }

    private boolean respondio(Usuario usuario) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM admin.nps_respuestas WHERE usuario_id = ?)",
                Boolean.class, usuario.getId()));
    }

    private boolean tuvoClase(Usuario usuario) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                         SELECT 1 FROM aula.sesiones_aprendizaje s
                           JOIN reservas.reservas r ON r.id = s.reserva_id
                          WHERE s.estado IN ('finalizada', 'finalizada_anticipada')
                            AND (r.pagador_id = ? OR r.beneficiario_id = ? OR r.tutor_id = ?))
                    OR EXISTS (SELECT 1 FROM reputacion.calificaciones WHERE autor_id = ?)""",
                Boolean.class, usuario.getId(), usuario.getId(), usuario.getId(), usuario.getId()));
    }
}
