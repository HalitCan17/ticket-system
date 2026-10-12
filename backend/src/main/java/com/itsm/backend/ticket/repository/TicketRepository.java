package com.itsm.backend.ticket.repository;

import java.time.Instant;
import java.util.List;

import com.itsm.backend.ticket.domain.Ticket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** List queries use {@link TicketSpecifications}. */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /**
     * Ids of RESOLVED tickets resolved at or before {@code cutoff}, in id order after {@code afterId}
     * (keyset paging, so a ticket that keeps failing cannot stall the scan).
     */
    @Query("""
            select t.id from Ticket t
            where t.status = com.itsm.backend.ticket.domain.TicketStatus.RESOLVED
              and t.resolvedAt <= :cutoff
              and t.id > :afterId
            order by t.id""")
    List<Long> findAutoCloseCandidateIds(@Param("cutoff") Instant cutoff, @Param("afterId") long afterId,
            Pageable pageable);

    /** Open tickets of a group that are assigned to a given user (released when the user leaves the group). */
    @Query("""
            select t from Ticket t
            where t.groupId = :groupId and t.assigneeId = :assigneeId
              and t.status in (com.itsm.backend.ticket.domain.TicketStatus.NEW,
                               com.itsm.backend.ticket.domain.TicketStatus.IN_PROGRESS,
                               com.itsm.backend.ticket.domain.TicketStatus.WAITING_FOR_CUSTOMER)
            order by t.id""")
    List<Ticket> findOpenInGroupAssignedTo(@Param("groupId") long groupId, @Param("assigneeId") String assigneeId);

    /**
     * Ids of open (not RESOLVED/CLOSED) tickets whose SLA is exceeded at {@code now} and not yet notified,
     * in id order after {@code afterId}. The elapsed time is computed like
     * {@link com.itsm.backend.ticket.domain.SlaClock#elapsed}.
     */
    @Query(value = """
            select id from tickets
            where sla_breach_notified_at is null
              and status not in ('RESOLVED', 'CLOSED')
              and sla_elapsed_seconds
                  + coalesce(extract(epoch from (cast(:now as timestamptz) - sla_running_since)), 0)
                  > sla_target_seconds
              and id > :afterId
            order by id
            limit :limit""", nativeQuery = true)
    List<Long> findSlaBreachCandidateIds(@Param("now") Instant now, @Param("afterId") long afterId,
            @Param("limit") int limit);
}
