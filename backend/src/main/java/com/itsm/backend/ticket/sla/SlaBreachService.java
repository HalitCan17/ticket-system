package com.itsm.backend.ticket.sla;

import java.time.Instant;
import java.util.List;

import com.itsm.backend.notification.NotificationService;
import com.itsm.backend.ticket.domain.SlaState;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finds open tickets that exceeded their resolution SLA and notifies once per ticket. Each ticket is
 * handled in its own transaction (flag + notifications commit together); see docs/adr/0007-in-app-notifications.md.
 */
@Service
public class SlaBreachService {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachService.class);
    private static final int BATCH_SIZE = 100;

    private final TicketRepository tickets;
    private final NotificationService notifications;
    private final TransactionTemplate transaction;

    public SlaBreachService(TicketRepository tickets, NotificationService notifications,
            PlatformTransactionManager transactionManager) {
        this.tickets = tickets;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** @return number of tickets whose breach was notified */
    public int notifyBreaches(Instant now) {
        int notified = 0;
        long afterId = 0;
        List<Long> batch;
        do {
            batch = tickets.findSlaBreachCandidateIds(now, afterId, BATCH_SIZE);
            for (Long id : batch) {
                if (notifyOne(id, now)) {
                    notified++;
                }
                afterId = id;
            }
        } while (batch.size() == BATCH_SIZE);
        return notified;
    }

    private boolean notifyOne(long id, Instant now) {
        try {
            Boolean result = transaction.execute(status -> {
                Ticket ticket = tickets.findById(id).orElse(null);
                // Re-check with the domain clock inside the transaction.
                if (ticket == null || ticket.getSlaBreachNotifiedAt() != null
                        || ticket.getSlaState() == SlaState.STOPPED || !ticket.getSlaClock().isBreached(now)) {
                    return false;
                }
                ticket.markSlaBreachNotified(now);
                notifications.notifySlaBreach(ticket);
                return true;
            });
            return Boolean.TRUE.equals(result);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Ticket {} changed while notifying its SLA breach; retried on the next run", id);
            return false;
        } catch (RuntimeException e) {
            log.warn("SLA breach notification failed for ticket {}", id, e);
            return false;
        }
    }
}
