package com.tinku.admin.repository;

import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a {@code admin.tickets_soporte}. La cola de cada rol se filtra por
 * {@code rolAsignado} (un Admin de Soporte Financiero no ve tickets de
 * Moderación — FR-ADM-008) con los abiertos primero y los más nuevos arriba.
 */
public interface TicketSoporteRepository extends JpaRepository<TicketSoporte, UUID> {

    List<TicketSoporte> findByRolAsignadoOrderByCreadoEnDesc(RolAdmin rolAsignado);
}