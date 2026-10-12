package com.itsm.backend.ticket.sla;

import java.time.Instant;
import java.util.List;
import java.util.function.BiPredicate;

import com.itsm.backend.notification.NotificationService;
import com.itsm.backend.ticket.domain.SlaState;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.repository.TicketActivityRepository;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.user.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Watches the SLA clocks: warns at 80 % of the resolution target, reports late first responses, and escalates tickets
 * whose resolution SLA is exceeded. Each situation is notified once per ticket; each ticket is handled in its own
 * transaction (flag + notifications commit together). See docs/adr/0007-in-app-notifications.md and
 * docs/adr/0015-sla-extensions.md.
 */
@Service
public class SlaBreachService {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachService.class);
    private static final int BATCH_SIZE = 100;

    /** One scan: candidate ids in id order after a given id. */
    private interface CandidateFinder {
        List<Long> find(Instant now, long afterId, int limit);
    }

    /** Counts of one run of {@link #run}. */
    public record Result(int warned, int firstResponseBreaches, int escalated) {
        public boolean any() {
            return warned + firstResponseBreaches + escalated > 0;
        }
    }

    private final TicketRepository tickets;
    private final TicketActivityRepository activities;
    private final NotificationService notifications;
    private final SlaProperties sla;
    private final TransactionTemplate transaction;

    public SlaBreachService(TicketRepository tickets, TicketActivityRepository activities,
            NotificationService notifications, SlaProperties sla, PlatformTransactionManager transactionManager) {
        this.tickets = tickets;
        this.activities = activities;
        this.notifications = notifications;
        this.sla = sla;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** All three scans, as the scheduler runs them. */
    public Result run(Instant now) {
        return new Result(notifyWarnings(now), notifyFirstResponseBreaches(now), notifyBreaches(now));
    }

    /** @return number of tickets warned about (80 % of the resolution SLA used) */
    public int notifyWarnings(Instant now) {
        return scan(tickets::findSlaWarningCandidateIds, now, (ticket, at) -> {
            // Re-check with the domain clock inside the transaction.
            if (ticket.getSlaWarningNotifiedAt() != null || ticket.getSlaState() != SlaState.RUNNING
                    || !ticket.getSlaClock().isWarningDue(at) || ticket.getSlaClock().isBreached(at)) {
                return false;
            }
            ticket.markSlaWarningNotified(at);
            notifications.notifySlaWarning(ticket);
            return true;
        });
    }

    /** @return number of tickets whose late first response was reported */
    public int notifyFirstResponseBreaches(Instant now) {
        return scan(tickets::findFirstResponseBreachCandidateIds, now, (ticket, at) -> {
            if (ticket.getFirstResponseBreachNotifiedAt() != null || !ticket.isAwaitingFirstResponse()
                    || !ticket.isFirstResponseBreached(at)) {
                return false;
            }
            ticket.markFirstResponseBreachNotified(at);
            notifications.notifyFirstResponseBreach(ticket);
            return true;
        });
    }

    /** @return number of tickets escalated because their resolution SLA was exceeded */
    public int notifyBreaches(Instant now) {
        return scan(tickets::findSlaBreachCandidateIds, now, (ticket, at) -> {
            if (ticket.getSlaBreachNotifiedAt() != null || ticket.getSlaState() == SlaState.STOPPED
                    || !ticket.getSlaClock().isBreached(at)) {
                return false;
            }
            ticket.markSlaBreachNotified(at);
            TicketPriority previous = ticket.escalate(sla.escalation().raisePriority(), sla::targetFor,
                    sla::firstResponseFor, at);
            if (previous != null) {
                activities.save(new TicketActivity(ticket.getId(), TicketActivityType.PRIORITY_CHANGED,
                        previous.name(), ticket.getPriority().name(), "SLA aşıldı: otomatik eskalasyon",
                        UserDirectory.SYSTEM_USER_ID, at));
            }
            notifications.notifySlaBreach(ticket);
            log.info("Ticket {} escalated (SLA breached){}", ticket.getId(),
                    previous == null ? "" : ", priority " + previous + " -> " + ticket.getPriority());
            return true;
        });
    }

    private int scan(CandidateFinder finder, Instant now, BiPredicate<Ticket, Instant> handler) {
        int handled = 0;
        long afterId = 0;
        List<Long> batch;
        do {
            batch = finder.find(now, afterId, BATCH_SIZE);
            for (Long id : batch) {
                if (handleOne(id, now, handler)) {
                    handled++;
                }
                afterId = id;
            }
        } while (batch.size() == BATCH_SIZE);
        return handled;
    }

    private boolean handleOne(long id, Instant now, BiPredicate<Ticket, Instant> handler) {
        try {
            Boolean result = transaction.execute(status -> {
                Ticket ticket = tickets.findById(id).orElse(null);
                return ticket != null && handler.test(ticket, now);
            });
            return Boolean.TRUE.equals(result);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Ticket {} changed while checking its SLA; retried on the next run", id);
            return false;
        } catch (RuntimeException e) {
            log.warn("SLA check failed for ticket {}", id, e);
            return false;
        }
    }
}
