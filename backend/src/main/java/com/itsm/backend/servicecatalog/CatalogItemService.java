package com.itsm.backend.servicecatalog;

import java.time.Clock;
import java.util.List;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.InvalidFieldsException;
import com.itsm.backend.common.error.InvalidFieldsException.FieldProblem;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.servicecatalog.CatalogDtos.ItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everybody reads the catalog (customers only the active items); managers maintain it. */
@Service
public class CatalogItemService {

    private static final Logger log = LoggerFactory.getLogger(CatalogItemService.class);

    private final CatalogItemRepository items;
    private final Clock clock;

    public CatalogItemService(CatalogItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CatalogItem> list(CurrentUser user) {
        return user.isStaff() ? items.findAllByName() : items.findActiveByName();
    }

    @Transactional(readOnly = true)
    public CatalogItem get(CurrentUser user, long id) {
        CatalogItem item = find(id);
        if (!item.isActive() && !user.isStaff()) {
            throw notFound(id);
        }
        return item;
    }

    @Transactional
    public CatalogItem create(CurrentUser user, ItemRequest request) {
        requireManager(user);
        CatalogItem item = new CatalogItem(user.id(), clock.instant());
        apply(user, item, request, -1);
        CatalogItem created = save(item);
        log.info("Catalog item {} created by {}", created.getId(), user.id());
        return created;
    }

    @Transactional
    public CatalogItem update(CurrentUser user, long id, ItemRequest request) {
        requireManager(user);
        CatalogItem item = find(id);
        apply(user, item, request, id);
        save(item);
        log.info("Catalog item {} updated by {} (active={})", id, user.id(), item.isActive());
        return item;
    }

    CatalogItem find(long id) {
        return items.findById(id).orElseThrow(() -> notFound(id));
    }

    private void apply(CurrentUser user, CatalogItem item, ItemRequest request, long exceptId) {
        List<FieldProblem> problems = CatalogFormValidator.checkDefinition(request.fields());
        if (!problems.isEmpty()) {
            throw new InvalidFieldsException("INVALID_FORM", "The form definition is not valid.", problems);
        }
        String name = request.name().strip();
        if (items.nameTaken(name, exceptId)) {
            throw nameTaken();
        }
        item.update(name, request.description().strip(), request.category(), request.priority(),
                request.requiresApproval(), request.active(), request.fields(), user.id(), clock.instant());
    }

    /** The unique index still decides when two managers save the same name at the same time. */
    private CatalogItem save(CatalogItem item) {
        try {
            return items.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("CATALOG_ITEM_NOT_FOUND", "Catalog item " + id + " not found.");
    }

    private static ConflictException nameTaken() {
        return new ConflictException("NAME_TAKEN", "A catalog item with this name already exists.");
    }

    private static void requireManager(CurrentUser user) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can change the service catalog.");
        }
    }
}
