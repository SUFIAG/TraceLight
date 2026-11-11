package com.sentinel.identity.adapter.in.rest;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.exception.ResourceNotFoundException;
import com.sentinel.identity.adapter.out.persistence.BusinessEntityRepository;
import com.sentinel.identity.application.dto.KybRequest;
import com.sentinel.identity.application.service.KybService;
import com.sentinel.identity.domain.model.BusinessEntity;
import com.sentinel.identity.domain.model.UboNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyb")
@RequiredArgsConstructor
public class KybController {

    private final KybService kybService;
    private final BusinessEntityRepository businessEntityRepository;

    @PostMapping("/onboard")
    public ResponseEntity<ApiResponse<BusinessEntity>> onboard(
            @RequestBody @Valid KybRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(kybService.onboard(orgId(auth), request)));
    }

    @GetMapping("/businesses")
    public ResponseEntity<ApiResponse<Page<BusinessEntity>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                businessEntityRepository.findByOrganizationIdOrderByRiskScoreDesc(orgId(auth), PageRequest.of(page, size))));
    }

    @GetMapping("/businesses/{id}")
    public ResponseEntity<ApiResponse<BusinessEntity>> get(@PathVariable UUID id, Authentication auth) {
        BusinessEntity business = businessEntityRepository.findByIdAndOrganizationId(id, orgId(auth))
                .orElseThrow(() -> new ResourceNotFoundException("BusinessEntity", id.toString()));
        return ResponseEntity.ok(ApiResponse.success(business));
    }

    /** The beneficial-ownership chain, ordered by depth. */
    @GetMapping("/businesses/{id}/ownership")
    public ResponseEntity<ApiResponse<List<UboNode>>> ownership(@PathVariable UUID id, Authentication auth) {
        businessEntityRepository.findByIdAndOrganizationId(id, orgId(auth))
                .orElseThrow(() -> new ResourceNotFoundException("BusinessEntity", id.toString()));
        return ResponseEntity.ok(ApiResponse.success(kybService.ownershipTree(id)));
    }

    private UUID orgId(Authentication auth) {
        return UUID.fromString((String) auth.getCredentials());
    }
}
