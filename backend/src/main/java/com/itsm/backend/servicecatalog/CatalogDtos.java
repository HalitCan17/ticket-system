package com.itsm.backend.servicecatalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotNull TicketCategory category,
            @NotNull TicketPriority priority,
            boolean requiresApproval,
            boolean active,
            @NotNull @Valid List<CatalogField> fields) {
    }

    public record ItemResponse(long id, String name, String description, TicketCategory category,
            TicketPriority priority, boolean requiresApproval, boolean active, List<CatalogField> fields,
            Instant updatedAt) {

        static ItemResponse from(CatalogItem item) {
            return new ItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCategory(),
                    item.getPriority(), item.isRequiresApproval(), item.isActive(), item.getFields(),
                    item.getUpdatedAt());
        }
    }

    public record CreateRequest(@NotNull Long itemId, @Size(max = 20) Map<String, String> answers) {
    }

    public record DecisionRequest(@NotNull Boolean approved, @Size(max = 1000) String comment) {
    }

    /**
     * A service request as its requester and staff see it.
     *
     * @param fields the item's current form, so answers can be shown with their labels
     */
    public record RequestResponse(long id, long itemId, String itemName, boolean requiresApproval,
            List<CatalogField> fields, Map<String, String> answers, ServiceRequestStatus status, String requesterId,
            String requesterName, String approverId, String approverName, String decisionComment, Instant decidedAt,
            Long ticketId, Instant createdAt, boolean canCancel, boolean canDecide) {
    }
}
