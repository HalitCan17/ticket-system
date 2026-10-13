package com.itsm.backend.servicecatalog;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Names notifications use for service requests: the requested item's name. */
@Component
public class ServiceRequestTitles {

    private final ServiceRequestRepository requests;

    public ServiceRequestTitles(ServiceRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> titles(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return requests.findTitles(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }
}
