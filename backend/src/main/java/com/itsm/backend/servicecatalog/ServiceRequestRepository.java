package com.itsm.backend.servicecatalog;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    Page<ServiceRequest> findByRequesterId(String requesterId, Pageable pageable);

    Page<ServiceRequest> findByRequesterIdAndStatus(String requesterId, ServiceRequestStatus status, Pageable pageable);

    Page<ServiceRequest> findByStatus(ServiceRequestStatus status, Pageable pageable);

    long countByStatus(ServiceRequestStatus status);

    /** id and the requested item's name, for notification texts. */
    @Query("""
            select r.id, i.name from ServiceRequest r, CatalogItem i
            where i.id = r.itemId and r.id in :ids""")
    List<Object[]> findTitles(@Param("ids") Collection<Long> ids);
}
