package com.itsm.backend.ticket.repository;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByRequesterId(String requesterId, Pageable pageable);

    Page<Ticket> findByRequesterIdAndStatus(String requesterId, TicketStatus status, Pageable pageable);
}
