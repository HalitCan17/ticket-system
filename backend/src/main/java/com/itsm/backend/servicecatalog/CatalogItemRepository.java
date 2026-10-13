package com.itsm.backend.servicecatalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    @Query("select i from CatalogItem i order by lower(i.name), i.id")
    List<CatalogItem> findAllByName();

    @Query("select i from CatalogItem i where i.active = true order by lower(i.name), i.id")
    List<CatalogItem> findActiveByName();

    /** Same comparison as the unique index on {@code lower(name)}. */
    @Query("select count(i) > 0 from CatalogItem i where lower(i.name) = lower(:name) and i.id <> :exceptId")
    boolean nameTaken(@Param("name") String name, @Param("exceptId") long exceptId);
}
