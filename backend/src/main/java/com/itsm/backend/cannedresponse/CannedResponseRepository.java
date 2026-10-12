package com.itsm.backend.cannedresponse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CannedResponseRepository extends JpaRepository<CannedResponse, Long> {

    @Query("select c from CannedResponse c order by lower(c.title), c.id")
    List<CannedResponse> findAllByTitle();

    /** Same comparison as the unique index on {@code lower(title)}. */
    @Query("select count(c) > 0 from CannedResponse c where lower(c.title) = lower(:title) and c.id <> :exceptId")
    boolean titleTaken(@Param("title") String title, @Param("exceptId") long exceptId);
}
