package com.itsm.backend.group;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportGroupRepository extends JpaRepository<SupportGroup, Long> {

    @Query("select g from SupportGroup g order by lower(g.name), g.id")
    List<SupportGroup> findAllByName();

    /** Locks the group row so two tickets created at once cannot take the same round-robin turn. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SupportGroup g where g.id = :id")
    Optional<SupportGroup> findByIdForUpdate(@Param("id") long id);

    @Query("select count(g) > 0 from SupportGroup g where lower(g.name) = lower(:name) and g.id <> :exceptId")
    boolean nameTaken(@Param("name") String name, @Param("exceptId") long exceptId);
}
