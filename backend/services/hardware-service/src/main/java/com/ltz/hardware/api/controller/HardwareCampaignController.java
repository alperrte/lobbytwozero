package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.ArchiveCatalogEntryRequest;
import com.ltz.hardware.api.dto.request.SaveHardwareCampaignRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwareCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/campaigns")
public class HardwareCampaignController {
    private final HardwareCampaignService service;

    public HardwareCampaignController(HardwareCampaignService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "HW-14: Yetkili kişi kaynaklı kampanya kaydeder; otomatik sağlayıcı çağrılmaz")
    public ResponseEntity<HardwareCampaignResponse> create(@Valid @RequestBody SaveHardwareCampaignRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/hardware/campaigns/" + response.id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "HW-14: Kaynak, fiyat ve geçerlilik bilgisini güncel sürümle değiştirir")
    public ResponseEntity<HardwareCampaignResponse> update(@PathVariable UUID id,
            @Valid @RequestBody SaveHardwareCampaignRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "HW-14: Kampanyayı gerekçeli audit ile arşivler")
    public ResponseEntity<Void> archive(@PathVariable UUID id, @Valid @RequestBody ArchiveCatalogEntryRequest request) {
        service.archive(id, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "HW-14: Yalnız geçerlilik penceresindeki aktif ürün kampanyasını döndürür")
    public ResponseEntity<HardwareCampaignResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.detail(id));
    }

    @GetMapping
    @Operation(summary = "HW-14: Geçerli yerel kampanyaları gözlem zamanlarıyla sayfalar")
    public ResponseEntity<HardwareCatalogPageResponse<HardwareCampaignResponse>> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(service.list(productId, page, size));
    }
}
