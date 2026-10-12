package com.itsm.backend.ticket.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketScope;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.service.TicketSearch;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Builds the ticket list query from {@link TicketSearch}. */
public final class TicketSpecifications {

    private static final Pattern TICKET_ID = Pattern.compile("#?(\\d{1,18})");

    private TicketSpecifications() {
    }

    /**
     * @param requesterId when not null, only this requester's tickets (customers); staff pass null
     */
    public static Specification<Ticket> matching(TicketSearch search, String requesterId) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (requesterId != null) {
                where.add(cb.equal(root.get("requesterId"), requesterId));
            }
            if (search.status() != null) {
                where.add(cb.equal(root.get("status"), search.status()));
            }
            if (search.scope() == TicketScope.OPEN) {
                where.add(cb.notEqual(root.get("status"), TicketStatus.CLOSED));
            } else if (search.scope() == TicketScope.CLOSED) {
                where.add(cb.equal(root.get("status"), TicketStatus.CLOSED));
            }
            if (search.category() != null) {
                where.add(cb.equal(root.get("category"), search.category()));
            }
            String text = search.text() == null ? "" : search.text().strip();
            if (!text.isEmpty()) {
                // Both sides are lower-cased by the database, so the same rules apply (e.g. Turkish İ/I).
                Predicate inTitle = cb.like(cb.lower(root.get("title")),
                        cb.lower(cb.literal("%" + escapeLike(text) + "%")), '\\');
                var id = TICKET_ID.matcher(text);
                where.add(id.matches()
                        ? cb.or(inTitle, cb.equal(root.get("id"), Long.parseLong(id.group(1))))
                        : inTitle);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
