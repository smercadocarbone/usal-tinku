package com.tinku.matching;

import com.tinku.reservas.port.TarifaProveedor;
import com.tinku.reservas.service.HorariosDisponiblesService;
import com.tinku.reservas.service.TarifaNoConfiguradaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * FR-MATCH-013/014: suma a cada resultado el precio por hora y el próximo horario libre, y
 * aplica el precio máximo. Corre después del ranking: el orden no cambia y el matching-service
 * no se entera. Llamada síncrona in-process a M4/M5 (AGENTS §1.2); la disponibilidad sale de
 * {@link HorariosDisponiblesService}, la misma que usa la pantalla de reserva.
 */
@Service
public class DatosDeReservaResultados {

    private final TarifaProveedor tarifas;
    private final HorariosDisponiblesService horarios;

    public DatosDeReservaResultados(TarifaProveedor tarifas, HorariosDisponiblesService horarios) {
        this.tarifas = tarifas;
        this.horarios = horarios;
    }

    @Transactional(readOnly = true)
    public List<BusquedaResponse> completar(List<BusquedaResponse> resultados, BigDecimal precioMaxHora) {
        return resultados.stream()
                .map(r -> r.conDatosDeReserva(precioHora(r), horarios.proximoLibre(r.tutorId()).orElse(null)))
                .filter(r -> precioMaxHora == null
                        || (r.precioHora() != null && r.precioHora().compareTo(precioMaxHora) <= 0))
                .toList();
    }

    /** Sin tarifa configurada (y sin stub de dev) no se inventa un precio: null. */
    private BigDecimal precioHora(BusquedaResponse r) {
        try {
            return tarifas.precioHora(r.tutorId());
        } catch (TarifaNoConfiguradaException e) {
            return null;
        }
    }
}
