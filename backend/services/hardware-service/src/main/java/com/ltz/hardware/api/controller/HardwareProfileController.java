package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.CreateHardwareProfileRequest;
import com.ltz.hardware.api.dto.request.UpdateHardwareProfileRequest;
import com.ltz.hardware.api.dto.response.HardwareProfileResponse;
import com.ltz.hardware.api.dto.response.HardwareProfilePageResponse;
import com.ltz.hardware.api.dto.response.HardwareProfileDetailResponse;
import com.ltz.hardware.api.dto.response.HardwareStatisticsResponse;
import com.ltz.hardware.application.service.HardwareProfileService;
import com.ltz.hardware.application.service.HardwareStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/v1/hardware/profiles")
public class HardwareProfileController {
    private final HardwareProfileService service;
    private final HardwareStatisticsService statisticsService;

    public HardwareProfileController(HardwareProfileService service, HardwareStatisticsService statisticsService) {
        this.service = service;
        this.statisticsService = statisticsService;
    }

    @PostMapping
    @Operation(summary = "HW-01: Yalnız doğrulanmış aktöre ait bilgisayar profili oluşturur")
    public ResponseEntity<HardwareProfileResponse> create(@Valid @RequestBody CreateHardwareProfileRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/hardware/profiles/" + response.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "HW-03: Sahibi olunan bilgisayarın adını ve türünü günceller")
    public ResponseEntity<HardwareProfileResponse> update(@PathVariable UUID profileId,
            @Valid @RequestBody UpdateHardwareProfileRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.update(profileId, request));
    }

    @GetMapping
    @Operation(summary = "HW-04: Yalnız aktöre ait bilgisayarları sayfalar")
    public ResponseEntity<HardwareProfilePageResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(page, size));
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "HW-05: Sahibi olunan profilin teknik bileşenlerini sayfalı döndürür")
    public ResponseEntity<HardwareProfileDetailResponse> detail(@PathVariable UUID profileId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.detail(profileId, page, size));
    }

    @GetMapping("/statistics")
    @Operation(summary = "HW-15: Yalnız aktörün kaydettiği bilgisayar ve bileşenlerin özetini döndürür")
    public ResponseEntity<HardwareStatisticsResponse> statistics() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(statisticsService.statistics());
    }

    @DeleteMapping("/{profileId}")
    @Operation(summary = "HW-16: Sahibi olunan bilgisayar profilini ve bileşenlerini kalıcı siler")
    public ResponseEntity<Void> delete(@PathVariable UUID profileId,
            @RequestParam @PositiveOrZero long profileVersion) {
        service.delete(profileId, profileVersion);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
