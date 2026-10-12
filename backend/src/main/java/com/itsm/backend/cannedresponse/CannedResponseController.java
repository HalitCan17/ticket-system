package com.itsm.backend.cannedresponse;

import java.util.List;

import com.itsm.backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canned-responses")
public class CannedResponseController {

    private final CannedResponseService service;

    public CannedResponseController(CannedResponseService service) {
        this.service = service;
    }

    @GetMapping
    public List<CannedResponseResponse> list(CurrentUser user) {
        return service.list(user).stream().map(CannedResponseResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CannedResponseResponse create(CurrentUser user, @Valid @RequestBody CannedResponseRequest request) {
        return CannedResponseResponse.from(service.create(user, request));
    }

    @PutMapping("/{id}")
    public CannedResponseResponse update(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody CannedResponseRequest request) {
        return CannedResponseResponse.from(service.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable long id) {
        service.delete(user, id);
    }
}
