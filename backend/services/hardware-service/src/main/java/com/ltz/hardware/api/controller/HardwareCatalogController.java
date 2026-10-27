package com.ltz.hardware.api.controller;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.service.HardwareBrandService;
import com.ltz.hardware.application.service.HardwareCatalogService;
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
@RequestMapping("/api/v1/hardware/catalog")
public class HardwareCatalogController {
    private final HardwareBrandService brands;
    private final HardwareCatalogService products;

    public HardwareCatalogController(HardwareBrandService brands, HardwareCatalogService products) {
        this.brands = brands;
        this.products = products;
    }

    @PostMapping("/brands")
    @Operation(summary = "HW-10: Yetkili katalog yöneticisi marka oluşturur")
    public ResponseEntity<HardwareBrandResponse> createBrand(@Valid @RequestBody CreateHardwareBrandRequest request) {
        var response = brands.create(request);
        return created("/brands/" + response.id(), response);
    }

    @PutMapping("/brands/{id}")
    @Operation(summary = "HW-10: Marka adını güncel sürümle değiştirir")
    public ResponseEntity<HardwareBrandResponse> updateBrand(@PathVariable UUID id,
            @Valid @RequestBody UpdateHardwareBrandRequest request) {
        return ok(brands.update(id, request));
    }

    @DeleteMapping("/brands/{id}")
    @Operation(summary = "HW-10: Markayı referanslarını koruyarak arşivler")
    public ResponseEntity<Void> archiveBrand(@PathVariable UUID id,
            @Valid @RequestBody ArchiveCatalogEntryRequest request) {
        brands.archive(id, request);
        return archived();
    }

    @GetMapping("/brands/{id}")
    @Operation(summary = "HW-10: Aktif markayı doğrulanmış kullanıcıya sunar")
    public ResponseEntity<HardwareBrandResponse> brand(@PathVariable UUID id) { return ok(brands.detail(id)); }

    @GetMapping("/brands")
    @Operation(summary = "HW-10: Aktif markaları sabit sıralı sayfalar")
    public ResponseEntity<HardwareCatalogPageResponse<HardwareBrandResponse>> brands(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(brands.list(page, size));
    }

    @PostMapping("/products")
    @Operation(summary = "HW-10: Marka bağımsız genel model oluşturur")
    public ResponseEntity<HardwareProductResponse> createProduct(@Valid @RequestBody CreateHardwareProductRequest request) {
        var response = products.create(request);
        return created("/products/" + response.id(), response);
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "HW-10: Modeli ve opsiyonel üretici seçeneklerini değiştirir")
    public ResponseEntity<HardwareProductResponse> updateProduct(@PathVariable UUID id,
            @Valid @RequestBody UpdateHardwareProductRequest request) {
        return ok(products.update(id, request));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "HW-10: Modeli mevcut profil referanslarını koruyarak arşivler")
    public ResponseEntity<Void> archiveProduct(@PathVariable UUID id,
            @Valid @RequestBody ArchiveCatalogEntryRequest request) {
        products.archive(id, request);
        return archived();
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "HW-10: Model ve opsiyonel aktif üreticileri döndürür")
    public ResponseEntity<HardwareProductResponse> product(@PathVariable UUID id) { return ok(products.detail(id)); }

    @GetMapping("/products")
    @Operation(summary = "HW-11: Aktif modelleri model, marka, kategori ve kayıtlı teknik alanlarla filtreler")
    public ResponseEntity<HardwareCatalogPageResponse<HardwareProductResponse>> products(
            @Valid @ModelAttribute HardwareCatalogFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(products.list(filter, page, size));
    }

    private static <T> ResponseEntity<T> ok(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);
    }

    private static <T> ResponseEntity<T> created(String path, T value) {
        return ResponseEntity.created(URI.create("/api/v1/hardware/catalog" + path))
                .cacheControl(CacheControl.noStore().cachePrivate()).body(value);
    }

    private static ResponseEntity<Void> archived() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore().cachePrivate()).build();
    }
}
