package com.itsm.backend.kb;

import java.util.ArrayList;
import java.util.Set;

import com.itsm.backend.ticket.domain.TicketCategory;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

final class KbArticleSpecifications {

    private KbArticleSpecifications() {
    }

    /**
     * Articles in one of {@code visibilities}, optionally of one category and containing {@code text} in the title or
     * body. Title matches come first, then the most recently updated.
     */
    static Specification<KbArticle> matching(Set<KbVisibility> visibilities, TicketCategory category, String text) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            where.add(root.get("visibility").in(visibilities));
            if (category != null) {
                where.add(cb.equal(root.get("category"), category));
            }
            String needle = text == null ? "" : text.strip();
            Predicate inTitle = null;
            if (!needle.isEmpty()) {
                // Both sides are lower-cased by the database, so the same rules apply (e.g. Turkish İ/I).
                var pattern = cb.lower(cb.literal("%" + escapeLike(needle) + "%"));
                inTitle = cb.like(cb.lower(root.get("title")), pattern, '\\');
                where.add(cb.or(inTitle, cb.like(cb.lower(root.get("body")), pattern, '\\')));
            }
            if (query != null && query.getResultType() != Long.class) {
                var orders = new ArrayList<Order>();
                if (inTitle != null) {
                    orders.add(cb.asc(cb.selectCase().when(inTitle, 0).otherwise(1)));
                }
                orders.add(cb.desc(root.get("updatedAt")));
                orders.add(cb.desc(root.get("id")));
                query.orderBy(orders);
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
