package com.itsm.backend.attachment;

import java.time.Instant;
import java.util.Map;

import com.itsm.backend.ticket.domain.CommentVisibility;

/** @param canDelete whether the caller may delete it (uploader or manager, ticket not closed) */
public record AttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        CommentVisibility visibility,
        String uploaderId,
        String uploaderName,
        Instant createdAt,
        boolean canDelete) {

    public static AttachmentResponse from(AttachmentService.AttachmentView view, Map<String, String> names) {
        TicketAttachment a = view.attachment();
        return new AttachmentResponse(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
                a.getVisibility(), a.getUploaderId(), names.get(a.getUploaderId()), a.getCreatedAt(), view.canDelete());
    }
}
