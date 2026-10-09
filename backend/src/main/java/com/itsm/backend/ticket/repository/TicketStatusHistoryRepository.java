package com.itsm.backend.ticket.repository;

import java.util.List;

import com.itsm.backend.ticket.domain.TicketStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {

    List<TicketStatusHistory> findByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
