package com.itsm.backend.kb;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.kb.KbDtos.ArticleDetail;
import com.itsm.backend.kb.KbDtos.ArticleRequest;
import com.itsm.backend.kb.KbDtos.ArticleSummary;
import com.itsm.backend.kb.KbDtos.VoteRequest;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.TicketCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb/articles")
public class KbArticleController {

    private final KbArticleService service;

    public KbArticleController(KbArticleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ArticleSummary> search(CurrentUser user,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) KbVisibility visibility,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.of(service.search(user, q, category, visibility, PageRequest.of(page, size)),
                ArticleSummary::from);
    }

    @GetMapping("/{id}")
    public ArticleDetail get(CurrentUser user, @PathVariable long id) {
        return service.get(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleDetail create(CurrentUser user, @Valid @RequestBody ArticleRequest request) {
        return service.create(user, request);
    }

    @PutMapping("/{id}")
    public ArticleDetail update(CurrentUser user, @PathVariable long id, @Valid @RequestBody ArticleRequest request) {
        return service.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable long id) {
        service.delete(user, id);
    }

    @PutMapping("/{id}/vote")
    public ArticleDetail vote(CurrentUser user, @PathVariable long id, @Valid @RequestBody VoteRequest request) {
        return service.vote(user, id, request.helpful());
    }
}
