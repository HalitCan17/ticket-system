package com.itsm.backend.kb;

import java.time.Instant;

import com.itsm.backend.ticket.domain.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class KbDtos {

    private KbDtos() {
    }

    public record ArticleRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String body,
            @NotNull TicketCategory category,
            @NotNull KbVisibility visibility) {
    }

    public record VoteRequest(@NotNull Boolean helpful) {
    }

    /** A list row: the first part of the body is enough to recognise the article. */
    public record ArticleSummary(long id, String title, String excerpt, TicketCategory category, KbVisibility visibility,
            Instant updatedAt) {

        static final int EXCERPT_LENGTH = 200;

        static ArticleSummary from(KbArticle article) {
            String body = article.getBody().strip().replaceAll("\\s+", " ");
            String excerpt = body.length() > EXCERPT_LENGTH
                    ? body.substring(0, EXCERPT_LENGTH).stripTrailing() + "…"
                    : body;
            return new ArticleSummary(article.getId(), article.getTitle(), excerpt, article.getCategory(),
                    article.getVisibility(), article.getUpdatedAt());
        }
    }

    public record ArticleDetail(long id, String title, String body, TicketCategory category, KbVisibility visibility,
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, long helpfulYes, long helpfulNo,
            Boolean myVote) {

        static ArticleDetail of(KbArticle a, KbVoteStore.Votes votes) {
            return new ArticleDetail(a.getId(), a.getTitle(), a.getBody(), a.getCategory(), a.getVisibility(),
                    a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt(), votes.helpful(),
                    votes.notHelpful(), votes.mine());
        }
    }
}
