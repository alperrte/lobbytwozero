package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.SaveHardwareComponentRequest;
import com.ltz.hardware.api.dto.response.ComponentMutationResponse;
import com.ltz.hardware.application.service.HardwareComponentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hardware/profiles/{profileId}/components")
public class HardwareComponentController {
    private final HardwareComponentService service;

    public HardwareComponentController(HardwareComponentService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "HW-02: Sahibi olunan profile manuel veya katalog bileşeni ekler")
    public ResponseEntity<ComponentMutationResponse> add(@PathVariable UUID profileId,
            @Valid @RequestBody SaveHardwareComponentRequest request) {
        var response = service.add(profileId, request);
        return ResponseEntity.created(URI.create("/api/v1/hardware/profiles/" + profileId
                + "/components/" + response.component().id()))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(response);
    }

    @PutMapping("/{componentId}")
    @Operation(summary = "HW-03: Bileşeni değiştirir; istemcinin güncel profil sürümü gerekir")
    public ResponseEntity<ComponentMutationResponse> update(@PathVariable UUID profileId,
            @PathVariable UUID componentId, @Valid @RequestBody SaveHardwareComponentRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(service.update(profileId, componentId, request));
    }

    @DeleteMapping("/{componentId}")
    @Operation(summary = "HW-03: Sahibi olunan profilden bileşeni kaldırır")
    public ResponseEntity<Void> remove(@PathVariable UUID profileId, @PathVariable UUID componentId,
            @RequestParam @PositiveOrZero long profileVersion) {
        long version = service.remove(profileId, componentId, profileVersion);
        return ResponseEntity.noContent().header("X-Profile-Version", Long.toString(version))
                .cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
