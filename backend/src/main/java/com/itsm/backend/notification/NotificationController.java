package com.itsm.backend.notification;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.user.UserDirectory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationService notificationService;
    private final TicketRepository tickets;
    private final UserDirectory userDirectory;

    public NotificationController(NotificationService notificationService, TicketRepository tickets,
            UserDirectory userDirectory) {
        this.notificationService = notificationService;
        this.tickets = tickets;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<Notification> result = notificationService.list(user, unreadOnly, PageRequest.of(page, size, NEWEST_FIRST));
        List<Notification> items = result.getContent();
        // One query each for ticket titles and actor names of the whole page.
        Map<Long, String> titles = tickets.findAllById(items.stream().map(Notification::getTicketId).distinct().toList())
                .stream().collect(Collectors.toMap(Ticket::getId, Ticket::getTitle));
        Map<String, String> names = userDirectory.displayNames(items.stream().map(Notification::getActorId).toList());
        return PageResponse.of(result, toResponse(titles, names));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(CurrentUser user) {
        return Map.of("count", notificationService.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(CurrentUser user, @PathVariable long id) {
        notificationService.markRead(user, id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(CurrentUser user) {
        return Map.of("updated", notificationService.markAllRead(user));
    }

    private static Function<Notification, NotificationResponse> toResponse(Map<Long, String> titles,
            Map<String, String> names) {
        return n -> new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTicketId(),
                titles.get(n.getTicketId()),
                n.getTicketStatus(),
                n.getActorId(),
                n.getActorId() == null ? null : names.get(n.getActorId()),
                n.getCreatedAt(),
                n.isRead());
    }
}
