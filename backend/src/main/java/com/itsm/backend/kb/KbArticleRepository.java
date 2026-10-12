package com.itsm.backend.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long>, JpaSpecificationExecutor<KbArticle> {

    /** Same comparison as the unique index on {@code lower(title)}. */
    @Query("select count(a) > 0 from KbArticle a where lower(a.title) = lower(:title) and a.id <> :exceptId")
    boolean titleTaken(@Param("title") String title, @Param("exceptId") long exceptId);
}
