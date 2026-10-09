package com.itsm.backend.ticket.repository;

import java.util.List;

import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    List<TicketComment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    List<TicketComment> findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(Long ticketId, CommentVisibility visibility);
}
