package com.itsm.backend.cannedresponse;

import java.time.Clock;
import java.util.List;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff read templates; only managers change them. */
@Service
public class CannedResponseService {

    private static final Logger log = LoggerFactory.getLogger(CannedResponseService.class);

    private final CannedResponseRepository repository;
    private final Clock clock;

    public CannedResponseService(CannedResponseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CannedResponse> list(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("STAFF_ONLY", "Only staff can use canned responses.");
        }
        return repository.findAllByTitle();
    }

    @Transactional
    public CannedResponse create(CurrentUser user, CannedResponseRequest request) {
        requireManager(user);
        String title = request.title().strip();
        checkTitleFree(title, -1);
        CannedResponse created = save(new CannedResponse(title, request.body().strip(), request.visibility(), user.id(),
                clock.instant()));
        log.info("Canned response {} created by {}", created.getId(), user.id());
        return created;
    }

    @Transactional
    public CannedResponse update(CurrentUser user, long id, CannedResponseRequest request) {
        requireManager(user);
        CannedResponse existing = find(id);
        String title = request.title().strip();
        checkTitleFree(title, id);
        existing.update(title, request.body().strip(), request.visibility(), user.id(), clock.instant());
        save(existing);
        log.info("Canned response {} updated by {}", id, user.id());
        return existing;
    }

    @Transactional
    public void delete(CurrentUser user, long id) {
        requireManager(user);
        repository.delete(find(id));
        log.info("Canned response {} deleted by {}", id, user.id());
    }

    private CannedResponse find(long id) {
        return repository.findById(id).orElseThrow(
                () -> new NotFoundException("CANNED_RESPONSE_NOT_FOUND", "Canned response " + id + " not found."));
    }

    private void checkTitleFree(String title, long exceptId) {
        if (repository.titleTaken(title, exceptId)) {
            throw titleTaken();
        }
    }

    /** The unique index still decides when two managers save the same title at the same time. */
    private CannedResponse save(CannedResponse response) {
        try {
            return repository.saveAndFlush(response);
        } catch (DataIntegrityViolationException e) {
            throw titleTaken();
        }
    }

    private static ConflictException titleTaken() {
        return new ConflictException("TITLE_TAKEN", "A canned response with this title already exists.");
    }

    private static void requireManager(CurrentUser user) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can change canned responses.");
        }
    }
}
