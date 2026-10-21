package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwareComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/comparisons")
public class HardwareComparisonController {
    private final HardwareComparisonService service;
    public HardwareComparisonController(HardwareComparisonService service) { this.service = service; }

    @PostMapping("/profiles")
    @Operation(summary = "HW-09: İki owner profilin kayıt/adet ve bilinen kapasite özeti")
    public ResponseEntity<HardwareComparisonResponse<HardwareProfileComparisonItem>> profiles(
            @Valid @RequestBody CompareHardwareIdsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.profiles(request));
    }

    @PostMapping("/components")
    @Operation(summary = "HW-09: Aynı kategoride iki owner bileşeninin kayıtlı teknik değerleri")
    public ResponseEntity<HardwareComparisonResponse<HardwareTechnicalComparisonItem>> components(
            @Valid @RequestBody CompareHardwareComponentsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.components(request));
    }

    @PostMapping("/catalog-products")
    @Operation(summary = "HW-09: Aynı kategoride iki aktif genel katalog modelinin teknik değerleri")
    public ResponseEntity<HardwareComparisonResponse<HardwareTechnicalComparisonItem>> products(
            @Valid @RequestBody CompareHardwareIdsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.catalogProducts(request));
    }
}
