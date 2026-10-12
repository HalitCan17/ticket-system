package com.itsm.backend.kb;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.kb.KbDtos.ArticleDetail;
import com.itsm.backend.kb.KbDtos.ArticleRequest;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.TicketCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everyone reads PUBLIC articles; staff also see INTERNAL and DRAFT ones. Staff write, only managers delete. An article
 * a customer may not read is reported as missing, not forbidden.
 */
@Service
public class KbArticleService {

    private static final Logger log = LoggerFactory.getLogger(KbArticleService.class);
    private static final Set<KbVisibility> PUBLIC_ONLY = EnumSet.of(KbVisibility.PUBLIC);

    private final KbArticleRepository articles;
    private final KbVoteStore votes;
    private final Clock clock;

    public KbArticleService(KbArticleRepository articles, KbVoteStore votes, Clock clock) {
        this.articles = articles;
        this.votes = votes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<KbArticle> search(CurrentUser user, String text, TicketCategory category, KbVisibility visibility,
            Pageable pageable) {
        Set<KbVisibility> visible = PUBLIC_ONLY;
        if (user.isStaff()) {
            visible = visibility == null ? EnumSet.allOf(KbVisibility.class) : EnumSet.of(visibility);
        } else if (visibility != null && visibility != KbVisibility.PUBLIC) {
            return Page.empty(pageable);
        }
        return articles.findAll(KbArticleSpecifications.matching(visible, category, text), pageable);
    }

    @Transactional(readOnly = true)
    public ArticleDetail get(CurrentUser user, long id) {
        return detail(user, findReadable(user, id));
    }

    @Transactional
    public ArticleDetail create(CurrentUser user, ArticleRequest request) {
        requireStaff(user);
        String title = request.title().strip();
        checkTitleFree(title, -1);
        KbArticle created = save(new KbArticle(title, request.body().strip(), request.category(),
                request.visibility(), user.id(), clock.instant()));
        log.info("KB article {} created by {} ({})", created.getId(), user.id(), created.getVisibility());
        return detail(user, created);
    }

    @Transactional
    public ArticleDetail update(CurrentUser user, long id, ArticleRequest request) {
        requireStaff(user);
        KbArticle existing = find(id);
        String title = request.title().strip();
        checkTitleFree(title, id);
        existing.update(title, request.body().strip(), request.category(), request.visibility(), user.id(),
                clock.instant());
        save(existing);
        log.info("KB article {} updated by {} ({})", id, user.id(), existing.getVisibility());
        return detail(user, existing);
    }

    @Transactional
    public void delete(CurrentUser user, long id) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can delete knowledge base articles.");
        }
        articles.delete(find(id));
        log.info("KB article {} deleted by {}", id, user.id());
    }

    @Transactional
    public ArticleDetail vote(CurrentUser user, long id, boolean helpful) {
        KbArticle article = findReadable(user, id);
        votes.vote(id, user.id(), helpful, clock.instant());
        return detail(user, article);
    }

    private ArticleDetail detail(CurrentUser user, KbArticle article) {
        return ArticleDetail.of(article, votes.votes(article.getId(), user.id()));
    }

    private KbArticle find(long id) {
        return articles.findById(id).orElseThrow(
                () -> new NotFoundException("KB_ARTICLE_NOT_FOUND", "Article " + id + " not found."));
    }

    private KbArticle findReadable(CurrentUser user, long id) {
        KbArticle article = find(id);
        if (!user.isStaff() && article.getVisibility() != KbVisibility.PUBLIC) {
            throw new NotFoundException("KB_ARTICLE_NOT_FOUND", "Article " + id + " not found.");
        }
        return article;
    }

    private void checkTitleFree(String title, long exceptId) {
        if (articles.titleTaken(title, exceptId)) {
            throw titleTaken();
        }
    }

    /** The unique index still decides when two people save the same title at the same time. */
    private KbArticle save(KbArticle article) {
        try {
            return articles.saveAndFlush(article);
        } catch (DataIntegrityViolationException e) {
            throw titleTaken();
        }
    }

    private static ConflictException titleTaken() {
        return new ConflictException("TITLE_TAKEN", "An article with this title already exists.");
    }

    private static void requireStaff(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("STAFF_ONLY", "Only staff can write knowledge base articles.");
        }
    }
}
