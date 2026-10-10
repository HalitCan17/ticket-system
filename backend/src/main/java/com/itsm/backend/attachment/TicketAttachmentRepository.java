package com.itsm.backend.attachment;

import java.util.List;
import java.util.Optional;

import com.itsm.backend.ticket.domain.CommentVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    List<TicketAttachment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    List<TicketAttachment> findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(Long ticketId,
            CommentVisibility visibility);

    Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);
}
