package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.reservas.service.SoloTutorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Mis cobros" del Tutor (R5, diseño §3, FR-PAG-008 como historial): lo que se le debe, lo
 * liberado y lo reembolsado, por clase.
 *
 * <ul>
 *   <li>El adicional de resumen es de la plataforma: nunca figura como ingreso del Tutor.</li>
 *   <li>Una pausa (denuncia o alerta) se muestra como "en revisión", sin el motivo.</li>
 * </ul>
 */
@Service
public class CobrosTutorService {

    static final int MAXIMO = 200;

    private final TransaccionRepository transaccionRepo;

    public CobrosTutorService(TransaccionRepository transaccionRepo) {
        this.transaccionRepo = transaccionRepo;
    }

    @Transactional(readOnly = true)
    public MisCobros de(Usuario tutor) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo un tutor tiene cobros.");
        }
        List<Cobro> cobros = transaccionRepo.cobrosDelTutor(tutor.getId(),
                        org.springframework.data.domain.PageRequest.of(0, MAXIMO)).stream()
                .map(fila -> cobro((Transaccion) fila[0], (Reserva) fila[1]))
                .toList();
        BigDecimal retenido = sumar(cobros, "retenido");
        BigDecimal enRevision = sumar(cobros, "en_revision");
        BigDecimal liberado = sumar(cobros, "liberado");
        BigDecimal reembolsado = sumar(cobros, "reembolsado");
        return new MisCobros(retenido, enRevision, liberado, reembolsado, cobros);
    }

    /**
     * FR-PAG-020: los cobros de un mes (por fecha de cobro, hora de Argentina) en CSV, para
     * facturar. Sale el DNI del pagador solo si es un adulto: es el receptor de la factura. El
     * alumno figura por nombre. Las transacciones de prueba (bypass) no salen.
     */
    @Transactional(readOnly = true)
    public String exportarMes(Usuario tutor, YearMonth mes) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException("Solo un tutor tiene cobros.");
        }
        Instant desde = mes.atDay(1).atStartOfDay(ReservasZonaHoraria.ZONA).toInstant();
        Instant hasta = mes.plusMonths(1).atDay(1).atStartOfDay(ReservasZonaHoraria.ZONA).toInstant();
        StringBuilder csv = new StringBuilder(
                "Fecha de cobro;Fecha de la clase;Pagó;DNI de quien pagó;Alumno;Precio de la clase;"
                        + "Comisión Tinku;Neto;Estado;Operación MercadoPago\r\n");
        for (Object[] fila : transaccionRepo.cobrosDelTutorEntre(tutor.getId(), desde, hasta)) {
            Transaccion t = (Transaccion) fila[0];
            Reserva r = (Reserva) fila[1];
            Cobro c = cobro(t, r);
            Usuario pagador = r.getPagador();
            String dniPagador = pagador != null && pagador.getTipo() != TipoUsuario.MENOR ? pagador.getDni() : "";
            csv.append(FECHA.format(t.getCreatedAt())).append(';')
                    .append(FECHA_HORA.format(r.getHorario())).append(';')
                    .append(celda(pagador == null ? "" : pagador.getNombre() + " " + pagador.getApellido())).append(';')
                    .append(celda(dniPagador)).append(';')
                    .append(celda(c.alumnoNombre() + " " + c.alumnoApellido())).append(';')
                    .append(importe(c.precioSesion())).append(';')
                    .append(importe(c.comision())).append(';')
                    .append(importe(c.neto())).append(';')
                    .append(ESTADO_LEGIBLE.get(c.estado())).append(';')
                    .append(celda(t.idPagoMp())).append("\r\n");
        }
        return csv.toString();
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ReservasZonaHoraria.ZONA);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ReservasZonaHoraria.ZONA);
    private static final Map<String, String> ESTADO_LEGIBLE = Map.of(
            "retenido", "A liberar", "en_revision", "En revisión",
            "liberado", "Liberado", "reembolsado", "Devuelto al alumno");

    /** Coma decimal (Excel en español) y sin separador de miles. */
    private static String importe(BigDecimal monto) {
        return monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Escapa {@code ;}, comillas y saltos, y neutraliza fórmulas (inyección CSV). */
    static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String v = valor.strip();
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static Cobro cobro(Transaccion t, Reserva r) {
        BigDecimal adicional = t.getMontoAdicionalResumen() == null ? BigDecimal.ZERO : t.getMontoAdicionalResumen();
        BigDecimal precioSesion = t.getMontoBruto().subtract(adicional);
        BigDecimal comision = t.getEstado() == EstadoTransaccion.REEMBOLSADO && t.getComisionPlataforma().signum() == 0
                ? BigDecimal.ZERO : t.getComisionPlataforma();
        return new Cobro(r.getId(), r.getHorario(), r.getBeneficiario().getNombre(), r.getBeneficiario().getApellido(),
                precioSesion, comision, precioSesion.subtract(comision), estado(t.getEstado()), t.getLiberarAt(),
                t.isEnBypass());
    }

    private static String estado(EstadoTransaccion e) {
        return switch (e) {
            case RETENIDO_ESCROW -> "retenido";
            case PAUSADO_DENUNCIA, PAUSADO_ALERTA -> "en_revision";
            case LIBERADO -> "liberado";
            case REEMBOLSADO -> "reembolsado";
        };
    }

    private static BigDecimal sumar(List<Cobro> cobros, String estado) {
        return cobros.stream().filter(c -> c.estado().equals(estado) && !c.simulado())
                .map(c -> estado.equals("reembolsado") ? c.precioSesion() : c.neto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Cobro(UUID reservaId, Instant horario, String alumnoNombre, String alumnoApellido,
                        BigDecimal precioSesion, BigDecimal comision, BigDecimal neto, String estado,
                        Instant liberaAt, boolean simulado) {
    }

    /** Totales en neto para el Tutor; {@code reembolsado} es lo que se le devolvió al alumno. */
    public record MisCobros(BigDecimal retenido, BigDecimal enRevision, BigDecimal liberado,
                            BigDecimal reembolsado, List<Cobro> cobros) {
    }
}
