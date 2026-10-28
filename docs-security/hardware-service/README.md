# hardware-service: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-28). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Alper (@alperrte)  •  **Eski borç:** ⚠️ 213 ihlal, 86 dosya

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç: kural bazında

| Kural | Adet | Ne demek | Nasıl düzeltilir |
|---|---:|---|---|
| `checkstyle:AvoidStarImport` | 213 | Yıldızlı import (`import x.*;`) | Her sınıfı tek tek import et. IDE'de "Optimize Imports"; IntelliJ'de "Class count to use import with '*'" değerini 999 yap. |

## Eski borç: dosya dosya

- `src/main/java/com/ltz/hardware/api/controller/HardwareBatchController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/controller/HardwareCampaignController.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/api/controller/HardwareCatalogController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/api/controller/HardwareComparisonController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/api/controller/HardwareComponentController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/controller/HardwarePrivacyController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/api/controller/HardwareReviewController.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/hardware/api/controller/HardwareReviewModerationController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/api/controller/HardwareReviewReportController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/controller/HardwareScanController.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/api/controller/HardwareSyncController.java`: checkstyle:AvoidStarImport ×5
- `src/main/java/com/ltz/hardware/api/dto/request/ApplyHardwareSyncRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/ArchiveCatalogEntryRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/CreateHardwareBrandRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/CreateHardwareProductRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/CreateHardwareReviewReportRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/CreateHardwareReviewRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/GrantHardwareSyncRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/HardwareBatchRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/HardwareCatalogFilter.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/HardwareScanRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/ModerateHardwareReviewRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/PreviewHardwareScanRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/SaveHardwareCampaignRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/SaveHardwareComponentRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/ScannedHardwareComponentRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/UpdateHardwareBrandRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/UpdateHardwarePrivacyRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/UpdateHardwareProductRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/UpdateHardwareProfileRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/request/UpdateHardwareReviewRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/api/dto/response/HardwarePrivacyResponse.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/application/mapper/HardwareSyncMapper.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/application/service/HardwareBatchService.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/application/service/HardwareBrandService.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/application/service/HardwareCampaignService.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/hardware/application/service/HardwareCatalogService.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/application/service/HardwareComparisonService.java`: checkstyle:AvoidStarImport ×6
- `src/main/java/com/ltz/hardware/application/service/HardwareComponentService.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/application/service/HardwarePrivacyService.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/hardware/application/service/HardwareReviewModerationService.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/application/service/HardwareReviewReportService.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/hardware/application/service/HardwareReviewService.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/hardware/application/service/HardwareScanService.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/application/service/HardwareSyncConsentService.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/application/service/HardwareSyncService.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/hardware/domain/entity/HardwareCampaign.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareComponent.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareProduct.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareProfile.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareReview.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareReviewReport.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareScan.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/domain/entity/HardwareSyncConsent.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/hardware/domain/entity/HardwareSyncDigest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/messaging/outbox/HardwareModerationOutbox.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareBatchQuery.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareCatalogSearch.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareReviewRepository.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareReviewSearch.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareSyncConsentRepository.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/persistence/repository/HardwareSyncReceiptRepository.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/hardware/infrastructure/security/SecurityContextHardwareSyncDeviceAccess.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/hardware/api/controller/HardwareBatchControllerTest.java`: checkstyle:AvoidStarImport ×6
- `src/test/java/com/ltz/hardware/api/controller/HardwareCampaignControllerTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/hardware/api/controller/HardwareCatalogControllerTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/hardware/api/controller/HardwareCatalogSearchTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/hardware/api/controller/HardwareComparisonControllerTest.java`: checkstyle:AvoidStarImport ×5
- `src/test/java/com/ltz/hardware/api/controller/HardwareComponentControllerTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/hardware/api/controller/HardwarePrivacyControllerTest.java`: checkstyle:AvoidStarImport ×7
- `src/test/java/com/ltz/hardware/api/controller/HardwareProfileControllerTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/hardware/api/controller/HardwareReviewControllerTest.java`: checkstyle:AvoidStarImport ×8
- `src/test/java/com/ltz/hardware/api/controller/HardwareReviewModerationControllerTest.java`: checkstyle:AvoidStarImport ×9
- `src/test/java/com/ltz/hardware/api/controller/HardwareScanControllerTest.java`: checkstyle:AvoidStarImport ×6
- `src/test/java/com/ltz/hardware/api/controller/HardwareSyncControllerTest.java`: checkstyle:AvoidStarImport ×10
- `src/test/java/com/ltz/hardware/domain/entity/HardwareProfileTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/hardware/domain/entity/HardwareReviewTextTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/hardware/domain/entity/HardwareScanTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/hardware/domain/entity/HardwareSyncDomainTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/hardware/infrastructure/security/HardwareAuthIntegrationTest.java`: checkstyle:AvoidStarImport ×7
- `src/test/java/com/ltz/hardware/infrastructure/security/HardwareJwksConfigurationTest.java`: checkstyle:AvoidStarImport ×7
- `src/test/java/com/ltz/hardware/infrastructure/security/HardwareJwtTestTokens.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/hardware/infrastructure/security/ProductionAuthSupport.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/hardware/infrastructure/security/SecurityContextCatalogWriteAccessTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/hardware/infrastructure/security/SecurityContextHardwareReviewModerationAccessTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/hardware/infrastructure/security/SecurityContextHardwareSyncDeviceAccessTest.java`: checkstyle:AvoidStarImport ×5

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/hardware-service -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/hardware-service      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/hardware-service -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/hardware-service   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
