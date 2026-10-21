package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.HardwareBatchRequest;
import com.ltz.hardware.api.dto.response.HardwareBatchResponse;
import com.ltz.hardware.application.service.HardwareBatchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/profiles/batch")
public class HardwareBatchController {
    private final HardwareBatchService service;
    public HardwareBatchController(HardwareBatchService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "HW-17: Owner teknik profillerini sınırlı ve sayfalı toplu DTO olarak sunar")
    public ResponseEntity<HardwareBatchResponse> read(@Valid @RequestBody HardwareBatchRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.read(request));
    }
}
