# game-service: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-28). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Hamza (@HmzT270)  •  **Eski borç:** ⚠️ 293 ihlal, 120 dosya

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç: kural bazında

| Kural | Adet | Ne demek | Nasıl düzeltilir |
|---|---:|---|---|
| `checkstyle:AvoidStarImport` | 280 | Yıldızlı import (`import x.*;`) | Her sınıfı tek tek import et. IDE'de "Optimize Imports"; IntelliJ'de "Class count to use import with '*'" değerini 999 yap. |
| `checkstyle:UnusedImports` | 1 | Kullanılmayan import | Sil (Optimize Imports). |
| `pmd:CloseResource` | 5 | Kaynak (ExecutorService, HttpClient, stream) kapatılmıyor | Testte try-with-resources; üretim kodunda uygulama boyu yaşayan nesneyse gerekçeli çözüm (sahibine sor). |
| `pmd:CyclomaticComplexity` | 7 | Metot/sınıf çok dallı ve karmaşık | Davranışı değiştirmeden küçük metotlara böl; önce mevcut testlerin geçtiğinden emin ol. |

## Eski borç: dosya dosya

- `src/main/java/com/ltz/game/api/controller/AdminAchievementController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/AdminGameController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/api/controller/AdminGameIdentityController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/CatalogController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/api/controller/DeveloperContentController.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/api/controller/DeveloperContentModerationController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/DeveloperContentPublicController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/DeveloperFeedbackController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/api/controller/GameBatchController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/GameDiscoveryController.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/api/controller/GameFeedbackController.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/api/controller/GameMetadataController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/GameModuleController.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/controller/GameViewController.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/api/dto/request/AchievementRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/request/FeedbackRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/request/FeedbackStatusRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/request/LocalizationRequest.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/api/dto/request/ReassignGameReferenceRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/request/SplitGameReferenceRequest.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/response/CatalogStatusResponse.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/response/FeedbackResponse.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/dto/response/GameMetadataResponse.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/api/dto/response/GameModuleResponse.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/api/exception/GameExceptionHandler.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/application/mapper/GameMetadataMapper.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/application/mapper/GameViewMapper.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/application/port/GamePriceProvider.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/application/service/AdminGameService.java`: checkstyle:AvoidStarImport ×3, pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/application/service/CatalogImportService.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/application/service/DeveloperContentModerationService.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/application/service/DeveloperContentService.java`: checkstyle:AvoidStarImport ×2, checkstyle:UnusedImports ×1
- `src/main/java/com/ltz/game/application/service/EditorialCatalogService.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/application/service/GameDetailRefreshService.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/application/service/GameFeedbackService.java`: checkstyle:AvoidStarImport ×6
- `src/main/java/com/ltz/game/application/service/GameIdentityService.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/game/application/service/GameModuleService.java`: checkstyle:AvoidStarImport ×5
- `src/main/java/com/ltz/game/application/service/GameTypeClassifier.java`: pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/application/service/GameViewService.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/application/service/LocalizationService.java`: checkstyle:AvoidStarImport ×3, pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/application/service/MediaUrlPolicy.java`: pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/application/service/MetadataProvenanceService.java`: checkstyle:AvoidStarImport ×1, pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/domain/entity/CatalogTerm.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/DeveloperContentBase.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/Game.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/game/domain/entity/GameAchievementDefinition.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameAdminAudit.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameDeveloperContent.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameDeveloperFeedback.java`: checkstyle:AvoidStarImport ×3
- `src/main/java/com/ltz/game/domain/entity/GameDeveloperGrant.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameDeveloperMedia.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameExternalReference.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/domain/entity/GameFieldOverride.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameIdentityAudit.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/domain/entity/GameLanguageSupport.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameMedia.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameMetadataProvenance.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameProviderSyncState.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameSystemRequirement.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/domain/entity/GameUpdateNote.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/infrastructure/client/SteamGameProvider.java`: checkstyle:AvoidStarImport ×1, pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/infrastructure/persistence/repository/GameAchievementRepository.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/infrastructure/persistence/repository/GameDiscoveryRepository.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/infrastructure/persistence/repository/GameFeedbackRepository.java`: checkstyle:AvoidStarImport ×4
- `src/main/java/com/ltz/game/infrastructure/persistence/repository/GameProviderSyncStateRepository.java`: checkstyle:AvoidStarImport ×1
- `src/main/java/com/ltz/game/infrastructure/security/FeedbackPrivacyAndRateLimitFilter.java`: checkstyle:AvoidStarImport ×2
- `src/main/java/com/ltz/game/infrastructure/security/JwtDecoderConfiguration.java`: pmd:CyclomaticComplexity ×1
- `src/main/java/com/ltz/game/infrastructure/security/ServiceTokenDecoderConfiguration.java`: pmd:CloseResource ×1
- `src/test/java/com/ltz/game/AuthenticatedApiTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/api/controller/AccountErasureTest.java`: checkstyle:AvoidStarImport ×6
- `src/test/java/com/ltz/game/api/controller/AdminGameIdentityControllerTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/api/controller/AdminGameManagementTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/api/controller/BatchProtectionTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/api/controller/CatalogControllerTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/api/controller/CatalogTypeFilteringTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/api/controller/ConcurrencyIntegrationTest.java`: checkstyle:AvoidStarImport ×2, pmd:CloseResource ×1
- `src/test/java/com/ltz/game/api/controller/DeveloperContentTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/api/controller/DeveloperRevocationRaceTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/api/controller/GameBatchControllerTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/api/controller/GameDiscoveryControllerTest.java`: checkstyle:AvoidStarImport ×6
- `src/test/java/com/ltz/game/api/controller/GameFeedbackTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/api/controller/GameFeedbackTransactionTest.java`: checkstyle:AvoidStarImport ×9
- `src/test/java/com/ltz/game/api/controller/GameMetadataControllerTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/api/controller/GameModuleTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/api/controller/GameViewControllerTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/api/controller/LocalizationMetadataTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/application/service/AccountErasureServiceTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/application/service/CatalogDiscoveryPerformanceTest.java`: checkstyle:AvoidStarImport ×7
- `src/test/java/com/ltz/game/application/service/CatalogImportServiceTest.java`: checkstyle:AvoidStarImport ×7
- `src/test/java/com/ltz/game/application/service/GameIdentitySafetyTest.java`: checkstyle:AvoidStarImport ×2, pmd:CloseResource ×1
- `src/test/java/com/ltz/game/application/service/GameIdentityServiceTest.java`: checkstyle:AvoidStarImport ×5
- `src/test/java/com/ltz/game/application/service/GamePriceContractTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/application/service/GameTypeClassifierTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/application/service/LocalizationServiceTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/application/service/MediaUrlPolicyTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/application/service/MetadataProvenanceServiceTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/application/service/PhaseTwoCatalogTest.java`: checkstyle:AvoidStarImport ×10
- `src/test/java/com/ltz/game/application/service/ProviderRetryPolicyTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/client/SteamGameProviderTest.java`: checkstyle:AvoidStarImport ×6
- `src/test/java/com/ltz/game/infrastructure/client/SteamRequestBudgetTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/config/CatalogSchedulingConfigurationTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/infrastructure/config/SteamProviderConfigurationTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/persistence/AccountErasureMigrationTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/persistence/GameMetadataPersistenceTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/infrastructure/persistence/LocalizationMigrationTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/persistence/MigrationArchitectureTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/persistence/MigrationSchemaSupport.java`: pmd:CloseResource ×1
- `src/test/java/com/ltz/game/infrastructure/persistence/MigrationTransferApplicationTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/persistence/MigrationTransferTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/persistence/PhaseSixMigrationTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/security/AuthJwtContractTest.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/game/infrastructure/security/ClientAddressResolverTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/security/FailClosedSecurityTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/security/FeedbackRateLimitTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/security/LtzJwtAuthenticationConverterTest.java`: checkstyle:AvoidStarImport ×1
- `src/test/java/com/ltz/game/infrastructure/security/ProductionAuthSupport.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/security/RealAuthIntegrationTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/infrastructure/security/SecurityBoundaryTest.java`: checkstyle:AvoidStarImport ×2
- `src/test/java/com/ltz/game/infrastructure/security/ServiceTokenDecoderConfigurationTest.java`: checkstyle:AvoidStarImport ×3
- `src/test/java/com/ltz/game/infrastructure/security/TokenBucketRateLimiterTest.java`: checkstyle:AvoidStarImport ×1, pmd:CloseResource ×1

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/game-service -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/game-service      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/game-service -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/game-service   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
