# req-01 — hardware-service: pre-push "Code Quality (new violations)" kırmızı (8 ihlal, 6 kural satırı)

> Kimden: Event Service (Nisa) · Kime: hardware-service sahibi (Alper) · Tarih: 2026-11-09 · Durum: **AÇIK**
> Bu talep yalnız bildirimdir; Event Service tarafında hardware-service kodu değiştirilmedi.

## Bulgu

`.\pre-push\pre-push.cmd` (log: `logs/pre-push-2026-11-09T18-19-47-649Z-5944-66141a95.log`, "[CHECK] Code Quality (new violations)") `feature/event-service` dalında, `origin/main` (7a0c196) ile birebir aynı olan hardware-service dosyalarında baseline'ın (213) üzerine **8 yeni ihlal puanı** raporladı (şimdi 221):

| Dosya | Kural | İzin verilen | Şimdi |
| --- | --- | ---: | ---: |
| `.../domain/entity/HardwareProductSourceView.java` | `checkstyle:AvoidStarImport` | 0 | 1 |
| `.../infrastructure/client/IcecatProductParser.java` | `checkstyle:UnusedImports` | 0 | 2 |
| `.../infrastructure/client/IcecatHttpClient.java` | `pmd:CloseResource` | 0 | 1 |
| `.../infrastructure/client/IcecatHttpClient.java` | `pmd:CyclomaticComplexity` | 0 | 1 |
| `.../infrastructure/client/IcecatXmlReader.java` | `pmd:CyclomaticComplexity` | 0 | 2 |
| `.../infrastructure/storage/LocalCatalogMediaStorage.java` | `pmd:CyclomaticComplexity` | 0 | 1 |

(Yollar `backend/services/hardware-service/src/main/java/com/ltz/hardware/` altındadır.)

## Beklenen

- `AvoidStarImport`: `import x.*` yerine açık import.
- `UnusedImports`: kullanılmayan import'ları sil.
- `CloseResource`: kapatılabilir kaynak `try-with-resources` ile açılsın.
- `CyclomaticComplexity`: metodu daha küçük metotlara böl.

Kural kapatma, `@SuppressWarnings`/`NOPMD` veya baseline'ı büyütme yasaktır (`docs-security/hardware-service/README.md`).

## Doğrulama (sahibi çalıştırır)

```powershell
mvn -B -f backend/pom.xml -pl services/hardware-service -DskipTests test-compile pmd:pmd
node pre-push/ci/lint-ratchet.mjs check --modules services/hardware-service
```

## Etki

Bu ihlaller giderilmeden `main` tabanlı tüm dallarda pre-push "Code Quality" adımı kırmızı kalır.
