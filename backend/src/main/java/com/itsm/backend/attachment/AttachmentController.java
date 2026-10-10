package com.itsm.backend.attachment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.user.UserDirectory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserDirectory userDirectory;

    public AttachmentController(AttachmentService attachmentService, UserDirectory userDirectory) {
        this.attachmentService = attachmentService;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    public List<AttachmentResponse> list(CurrentUser user, @PathVariable long ticketId) {
        List<AttachmentService.AttachmentView> views = attachmentService.list(user, ticketId);
        Map<String, String> names = userDirectory.displayNames(
                views.stream().map(v -> v.attachment().getUploaderId()).toList());
        return views.stream().map(v -> AttachmentResponse.from(v, names)).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(CurrentUser user, @PathVariable long ticketId,
            @RequestParam("file") MultipartFile file, @RequestParam("visibility") CommentVisibility visibility) {
        AttachmentService.AttachmentView view = attachmentService.upload(user, ticketId, file, visibility);
        return AttachmentResponse.from(view, userDirectory.displayNames(List.of(user.id())));
    }

    /**
     * Always served as a download with the content type decided at upload, never inline, so an attachment
     * cannot run in the application's origin. {@code X-Content-Type-Options: nosniff} comes from Spring Security.
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<Resource> download(CurrentUser user, @PathVariable long ticketId,
            @PathVariable long attachmentId) {
        AttachmentService.AttachmentContent content = attachmentService.open(user, ticketId, attachmentId);
        TicketAttachment a = content.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .contentLength(a.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(a.getOriginalName()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(content.stream()));
    }

    /**
     * {@code attachment; filename="<ASCII fallback>"; filename*=UTF-8''<percent-encoded>} (RFC 6266). Spring's
     * builder would RFC 2047-encode the plain {@code filename}, which some clients show literally.
     */
    static String contentDisposition(String fileName) {
        StringBuilder fallback = new StringBuilder();
        fileName.codePoints().forEach(c -> fallback.append(
                c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? (char) c : '_'));
        // URLEncoder is form encoding: space becomes '+', and '*' is not an RFC 5987 attr-char.
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable long ticketId, @PathVariable long attachmentId) {
        attachmentService.delete(user, ticketId, attachmentId);
    }
}
