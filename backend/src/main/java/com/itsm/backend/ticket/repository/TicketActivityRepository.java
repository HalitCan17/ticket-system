package com.itsm.backend.ticket.repository;

import java.util.List;

import com.itsm.backend.ticket.domain.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

    List<TicketActivity> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
