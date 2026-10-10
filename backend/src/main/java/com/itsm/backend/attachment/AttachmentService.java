package com.itsm.backend.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.common.error.PayloadTooLargeException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ticket attachments. Visibility follows comments (INTERNAL is staff-only); attachments of tickets or
 * visibilities the caller cannot see answer 404. See docs/adr/0008-attachments.md.
 */
@Service
public class AttachmentService {

    /** An attachment together with whether the caller may delete it. */
    public record AttachmentView(TicketAttachment attachment, boolean canDelete) {
    }

    /** Download: metadata plus an open stream the caller must close. */
    public record AttachmentContent(TicketAttachment attachment, InputStream stream) {
    }

    private final TicketService ticketService;
    private final TicketAttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final AttachmentProperties properties;
    private final Clock clock;

    public AttachmentService(TicketService ticketService, TicketAttachmentRepository attachments,
            AttachmentStorage storage, AttachmentProperties properties, Clock clock) {
        this.ticketService = ticketService;
        this.attachments = attachments;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(CurrentUser user, long ticketId) {
        Ticket ticket = ticketService.getVisibleTicket(user, ticketId);
        List<TicketAttachment> visible = user.isStaff()
                ? attachments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)
                : attachments.findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(ticketId, CommentVisibility.EXTERNAL);
        return visible.stream().map(a -> new AttachmentView(a, canDelete(user, ticket, a))).toList();
    }

    @Transactional
    public AttachmentView upload(CurrentUser user, long ticketId, MultipartFile file, CommentVisibility visibility) {
        Ticket ticket = ticketService.getVisibleTicket(user, ticketId);
        if (visibility == CommentVisibility.INTERNAL && !user.isStaff()) {
            throw new ForbiddenException("INTERNAL_ATTACHMENT_NOT_ALLOWED", "Customers can only add external attachments.");
        }
        if (ticket.isClosed()) {
            throw new ConflictException("TICKET_CLOSED", "Attachments cannot be added to a closed ticket.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw tooLarge();
        }

        AttachmentTypePolicy.Accepted accepted = AttachmentTypePolicy.check(file.getOriginalFilename(), head(file));
        AttachmentStorage.StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store attachment", e);
        }
        // If the transaction does not commit, the stored file must not stay behind.
        afterCompletion(stored.key(), false);

        TicketAttachment attachment = attachments.save(new TicketAttachment(ticket.getId(), user.id(), visibility,
                accepted.fileName(), accepted.contentType(), stored, clock.instant()));
        return new AttachmentView(attachment, canDelete(user, ticket, attachment));
    }

    @Transactional(readOnly = true)
    public AttachmentContent open(CurrentUser user, long ticketId, long attachmentId) {
        ticketService.getVisibleTicket(user, ticketId);
        TicketAttachment attachment = findVisible(user, ticketId, attachmentId);
        try {
            return new AttachmentContent(attachment, storage.open(attachment.getStorageKey()));
        } catch (IOException e) {
            throw new UncheckedIOException("Attachment content missing: " + attachment.getStorageKey(), e);
        }
    }

    @Transactional
    public void delete(CurrentUser user, long ticketId, long attachmentId) {
        Ticket ticket = ticketService.getVisibleTicket(user, ticketId);
        TicketAttachment attachment = findVisible(user, ticketId, attachmentId);
        if (ticket.isClosed()) {
            throw new ConflictException("TICKET_CLOSED", "Attachments of a closed ticket cannot be deleted.");
        }
        if (!mayDelete(user, attachment)) {
            throw new ForbiddenException("ATTACHMENT_DELETE_NOT_ALLOWED",
                    "Only the uploader or a manager can delete this attachment.");
        }
        attachments.delete(attachment);
        // Row first, file after commit: at worst an orphan file remains, never a row without content.
        afterCompletion(attachment.getStorageKey(), true);
    }

    /** Call only after the ticket itself was checked with {@link TicketService#getVisibleTicket}. */
    private TicketAttachment findVisible(CurrentUser user, long ticketId, long attachmentId) {
        return attachments.findByIdAndTicketId(attachmentId, ticketId)
                .filter(a -> user.isStaff() || a.getVisibility() == CommentVisibility.EXTERNAL)
                .orElseThrow(() -> new NotFoundException("ATTACHMENT_NOT_FOUND",
                        "Attachment " + attachmentId + " not found."));
    }

    private static boolean canDelete(CurrentUser user, Ticket ticket, TicketAttachment attachment) {
        return !ticket.isClosed() && mayDelete(user, attachment);
    }

    private static boolean mayDelete(CurrentUser user, TicketAttachment attachment) {
        return user.role() == Role.MANAGER || user.id().equals(attachment.getUploaderId());
    }

    /** Deletes the stored file after commit ({@code onCommit}) or after a rollback ({@code !onCommit}). */
    private void afterCompletion(String storageKey, boolean onCommit) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) == onCommit) {
                    storage.delete(storageKey);
                }
            }
        });
    }

    private PayloadTooLargeException tooLarge() {
        return new PayloadTooLargeException("FILE_TOO_LARGE",
                "Attachments can be at most " + properties.maxFileSize().toMegabytes() + " MB.");
    }

    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(AttachmentTypePolicy.HEAD_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }
}
