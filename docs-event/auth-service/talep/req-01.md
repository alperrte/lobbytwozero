# req-01 — auth-service: pre-push "Code Quality (new violations)" kırmızı (2 PMD ihlali)

> Kimden: Event Service (Nisa) · Kime: auth-service sahibi (Kerem) · Tarih: 2026-11-09 · Durum: **AÇIK**
> Bu talep yalnız bildirimdir; Event Service tarafında auth-service kodu değiştirilmedi.

## Bulgu

`.\pre-push\pre-push.cmd` (log: `logs/pre-push-2026-11-09T18-19-47-649Z-5944-66141a95.log`, "[CHECK] Code Quality (new violations)") `feature/event-service` dalında, `origin/main` (7a0c196) ile birebir aynı olan auth-service dosyalarında **2 yeni ihlal** raporladı. Baseline'daki izin verilen sayı 0, şimdiki 1+1:

| Dosya | Kural | İzin verilen | Şimdi |
| --- | --- | ---: | ---: |
| `services/auth-service/src/main/java/com/ltz/auth/application/service/GoogleLoginService.java` | `pmd:PreserveStackTrace` | 0 | 1 |
| `services/auth-service/src/main/java/com/ltz/auth/application/service/SteamLoginService.java` | `pmd:PreserveStackTrace` | 0 | 1 |

Event Service satırı aynı raporda `0 | 0 | 0 | 0` (yeni ihlal yok).

## Beklenen

`PreserveStackTrace`: yakalanan istisnadan yeni istisna fırlatılırken özgün istisna `cause` olarak verilmelidir (veya bilinçli olarak atılıyorsa `catch` içinde `_`/kasıtlı yorumla ve kuralı kapatmadan, kodu değiştirerek çözülür). Kural kapatma, `@SuppressWarnings`/`NOPMD` veya baseline'ı büyütme yasaktır (`docs-security/auth-service/README.md`).

## Doğrulama (sahibi çalıştırır)

```powershell
mvn -B -f backend/pom.xml -pl services/auth-service -DskipTests test-compile pmd:pmd
node pre-push/ci/lint-ratchet.mjs check --modules services/auth-service
```

## Etki

Bu iki ihlal giderilmeden `main` tabanlı tüm dallarda pre-push "Code Quality" adımı kırmızı kalır.
