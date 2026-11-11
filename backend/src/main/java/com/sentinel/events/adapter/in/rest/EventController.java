package com.sentinel.events.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.events.application.dto.EventResponse;
import com.sentinel.events.application.service.EventStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventStoreService eventStoreService;

    /**
     * The raw event stream for an aggregate.
     *
     * The {@code event_store} table carries no organization column, so this
     * endpoint cannot be filtered by tenant at the query level. Until that
     * column exists it is restricted to administrators rather than left open to
     * every authenticated analyst.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{aggregateId}")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventStream(
            @PathVariable UUID aggregateId) {
        return ResponseEntity.ok(ApiResponse.success(eventStoreService.findByAggregate(aggregateId)));
    }
}
