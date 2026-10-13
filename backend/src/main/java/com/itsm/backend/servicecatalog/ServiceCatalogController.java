package com.itsm.backend.servicecatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.servicecatalog.CatalogDtos.CreateRequest;
import com.itsm.backend.servicecatalog.CatalogDtos.DecisionRequest;
import com.itsm.backend.servicecatalog.CatalogDtos.ItemRequest;
import com.itsm.backend.servicecatalog.CatalogDtos.ItemResponse;
import com.itsm.backend.servicecatalog.CatalogDtos.RequestResponse;
import com.itsm.backend.user.UserDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceCatalogController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("id"));

    private final CatalogItemService catalog;
    private final ServiceRequestService requests;
    private final CatalogItemRepository items;
    private final UserDirectory users;

    public ServiceCatalogController(CatalogItemService catalog, ServiceRequestService requests,
            CatalogItemRepository items, UserDirectory users) {
        this.catalog = catalog;
        this.requests = requests;
        this.items = items;
        this.users = users;
    }

    @GetMapping("/api/catalog/items")
    public List<ItemResponse> items(CurrentUser user) {
        return catalog.list(user).stream().map(ItemResponse::from).toList();
    }

    @GetMapping("/api/catalog/items/{id}")
    public ItemResponse item(CurrentUser user, @PathVariable long id) {
        return ItemResponse.from(catalog.get(user, id));
    }

    @PostMapping("/api/catalog/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(CurrentUser user, @Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(catalog.create(user, request));
    }

    @PutMapping("/api/catalog/items/{id}")
    public ItemResponse updateItem(CurrentUser user, @PathVariable long id, @Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(catalog.update(user, id, request));
    }

    @PostMapping("/api/service-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(CurrentUser user, @Valid @RequestBody CreateRequest request) {
        return toResponse(user, List.of(requests.create(user, request))).get(0);
    }

    /** Customers always get their own requests; staff get all unless {@code mine=true}. */
    @GetMapping("/api/service-requests")
    public PageResponse<RequestResponse> list(CurrentUser user,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) ServiceRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<ServiceRequest> result = requests.list(user, mine, status, PageRequest.of(page, size, NEWEST_FIRST));
        List<RequestResponse> rows = toResponse(user, result.getContent());
        return new PageResponse<>(rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/api/service-requests/{id}")
    public RequestResponse get(CurrentUser user, @PathVariable long id) {
        return toResponse(user, List.of(requests.get(user, id))).get(0);
    }

    @PostMapping("/api/service-requests/{id}/cancel")
    public RequestResponse cancel(CurrentUser user, @PathVariable long id) {
        return toResponse(user, List.of(requests.cancel(user, id))).get(0);
    }

    @PostMapping("/api/service-requests/{id}/decision")
    public RequestResponse decide(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody DecisionRequest request) {
        return toResponse(user, List.of(requests.decide(user, id, request))).get(0);
    }

    /** For the managers' menu badge. */
    @GetMapping("/api/service-requests/pending-count")
    public Map<String, Long> pendingCount(CurrentUser user) {
        return Map.of("count", requests.pendingApprovals(user));
    }

    private List<RequestResponse> toResponse(CurrentUser user, List<ServiceRequest> page) {
        Map<Long, CatalogItem> byId = items.findAllById(page.stream().map(ServiceRequest::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogItem::getId, i -> i));
        Set<String> people = page.stream()
                .flatMap(r -> Stream.of(r.getRequesterId(), r.getApproverId()))
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, String> names = users.displayNames(List.copyOf(people));
        return page.stream().map(r -> {
            CatalogItem item = byId.get(r.getItemId());
            return new RequestResponse(r.getId(), r.getItemId(), item.getName(), item.isRequiresApproval(),
                    item.getFields(), r.getOrderedAnswers(), r.getStatus(), r.getRequesterId(),
                    names.get(r.getRequesterId()), r.getApproverId(),
                    r.getApproverId() == null ? null : names.get(r.getApproverId()), r.getDecisionComment(),
                    r.getDecidedAt(), r.getTicketId(), r.getCreatedAt(), requests.canCancel(user, r),
                    requests.canDecide(user, r));
        }).toList();
    }
}
