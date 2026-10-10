package com.itsm.backend.ticket.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.ticket.repository.TicketStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Closes RESOLVED tickets the customer has not answered for {@code itsm.auto-close.after}.
 * Each ticket is closed in its own transaction so one failure does not affect the others.
 * See docs/adr/0005-sla-and-auto-close.md.
 */
@Service
public class TicketAutoCloseService {

    public static final String SYSTEM_ACTOR = "system";

    private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseService.class);
    private static final int BATCH_SIZE = 100;

    private final TicketRepository tickets;
    private final TicketStatusHistoryRepository history;
    private final AutoCloseProperties properties;
    private final TransactionTemplate transaction;

    public TicketAutoCloseService(TicketRepository tickets, TicketStatusHistoryRepository history,
            AutoCloseProperties properties, PlatformTransactionManager transactionManager) {
        this.tickets = tickets;
        this.history = history;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** @return number of tickets closed */
    public int closeStaleResolved(Instant now) {
        Instant cutoff = now.minus(properties.after());
        int closed = 0;
        long afterId = 0;
        List<Long> batch;
        do {
            batch = tickets.findAutoCloseCandidateIds(cutoff, afterId, PageRequest.ofSize(BATCH_SIZE));
            for (Long id : batch) {
                if (closeOne(id, cutoff, now)) {
                    closed++;
                }
                afterId = id;
            }
        } while (batch.size() == BATCH_SIZE);
        return closed;
    }

    private boolean closeOne(long id, Instant cutoff, Instant now) {
        try {
            Boolean result = transaction.execute(status -> {
                Ticket ticket = tickets.findById(id).orElse(null);
                // Re-check inside the transaction: the customer may have objected or closed meanwhile.
                if (ticket == null || ticket.getStatus() != TicketStatus.RESOLVED
                        || ticket.getResolvedAt() == null || ticket.getResolvedAt().isAfter(cutoff)) {
                    return false;
                }
                ticket.autoClose(now);
                history.save(new TicketStatusHistory(ticket.getId(), TicketStatus.RESOLVED, TicketStatus.CLOSED,
                        SYSTEM_ACTOR, "Auto-closed: no customer response for " + describe(properties.after()), now));
                return true;
            });
            return Boolean.TRUE.equals(result);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Ticket {} changed while auto-closing; skipped", id);
            return false;
        } catch (RuntimeException e) {
            log.warn("Auto-close failed for ticket {}", id, e);
            return false;
        }
    }

    /** "7d", "12h", "30m" or "45s" — the largest whole unit. */
    static String describe(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 86_400 == 0) {
            return seconds / 86_400 + "d";
        }
        if (seconds % 3_600 == 0) {
            return seconds / 3_600 + "h";
        }
        if (seconds % 60 == 0) {
            return seconds / 60 + "m";
        }
        return seconds + "s";
    }
}
