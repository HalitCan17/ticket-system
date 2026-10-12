package com.itsm.backend.ticket.dto;

import jakarta.validation.constraints.Size;

/** @param assigneeId the agent or manager to assign; null unassigns the ticket */
public record AssignTicketRequest(@Size(max = 64) String assigneeId) {
}
