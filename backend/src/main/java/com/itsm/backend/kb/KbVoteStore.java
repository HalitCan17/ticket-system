package com.itsm.backend.kb;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** One helpful / not helpful vote per user per article (table {@code kb_article_votes}). */
@Repository
public class KbVoteStore {

    /** The tallies of one article and the calling user's own vote ({@code null} = none). */
    public record Votes(long helpful, long notHelpful, Boolean mine) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public KbVoteStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void vote(long articleId, String userId, boolean helpful, Instant now) {
        jdbc.update("""
                insert into kb_article_votes (article_id, user_id, helpful, voted_at)
                values (:article, :user, :helpful, :now)
                on conflict (article_id, user_id)
                do update set helpful = excluded.helpful, voted_at = excluded.voted_at""",
                new MapSqlParameterSource()
                        .addValue("article", articleId)
                        .addValue("user", userId)
                        .addValue("helpful", helpful)
                        .addValue("now", Timestamp.from(now)));
    }

    public Votes votes(long articleId, String userId) {
        List<Boolean> mine = jdbc.queryForList(
                "select helpful from kb_article_votes where article_id = :article and user_id = :user",
                new MapSqlParameterSource().addValue("article", articleId).addValue("user", userId), Boolean.class);
        Map<Boolean, Long> counts = new HashMap<>();
        jdbc.query("select helpful, count(*) as n from kb_article_votes where article_id = :article group by helpful",
                new MapSqlParameterSource().addValue("article", articleId),
                rs -> {
                    counts.put(rs.getBoolean("helpful"), rs.getLong("n"));
                });
        return new Votes(counts.getOrDefault(true, 0L), counts.getOrDefault(false, 0L),
                mine.isEmpty() ? null : mine.get(0));
    }
}
