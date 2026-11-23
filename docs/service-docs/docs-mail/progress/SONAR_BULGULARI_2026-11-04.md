# Mail-service Sonar bulguları (devir notu) — 2026-11-04

Bu not Auth oturumundan bırakıldı. Mail oturumu önce bunu okuyup işe buradan başlayabilir.

## Kaynak ve dikkat

- Tarama `LTZ-AUTH/lobbytwozero` kopyasında (`feature/auth-service` dalı) çalıştırıldı: `sonar.cmd scan dev`.
- Bu mail reposu (`feature/mail-service`, son commit `ddb048a`, 2026-10-31) farklı olabilir.
  **Önce bu repoda aynı bulguların hâlâ geçerli olduğunu doğrula.** Gerekirse burada `sonar.cmd scan dev` çalıştır.
- Taramanın genel sonucu FAIL; ama auth-service'te hiç bulgu yok. Mail'in payı aşağıdaki iki bulgu.
- Bulguların gerçek olup olmadığı kodla doğrulanmadı. Sadece Sonar çıktısı ve ilgili iki satır okundu.

## Bulgular

| Önem | Kural | Yer | Sonar mesajı |
|---|---|---|---|
| MAJOR / BUG | java:S2583 | `MailDispatchService.java:88` | Koşul her zaman `false` |
| CRITICAL / VULNERABILITY | java:S4502 | `SecurityConfig.java:68` | Spring Security CSRF korumasını kapatmak güvenli mi? |

Dosyalar: `backend/services/mail-service/src/main/java/com/ltz/mail/...`
(`application/service/MailDispatchService.java`, `infrastructure/config/SecurityConfig.java`)

### 1. MailDispatchService.java:88 (S2583)

Satır: `return claimed == null ? List.of() : claimed;`

- Sonar `claimed` değerinin hiç `null` olmadığını düşünüyor. `claimed` büyük olasılıkla `TransactionTemplate.execute(...)` sonucu.
- Kontrol edilecek: `execute` burada gerçekten null dönebilir mi? Lambda her yolda liste dönüyor (boşsa `List.of()`, doluysa `saveAll`).
- Null kontrolünü körlemesine silme. Null olamıyorsa kontrolü kaldırmak doğru; Spring'in nullable bildirimi yüzünden
  savunma amaçlı tutuluyorsa doğru yol nullable'ı belgelemek ya da gerekçeli şekilde "Won't fix" işaretlemek.

### 2. SecurityConfig.java:68 (S4502)

Satır: `.csrf(csrf -> csrf.ignoringRequestMatchers(INTERNAL_API))`

- CSRF genel olarak kapatılmıyor; yalnızca iç API yollarında (`INTERNAL_API`) muaf. Kodda zaten gerekçe yorumu var:
  kimlik cookie ile okunmuyor, yalnız Bearer servis token'ı kullanılıyor, oturum STATELESS.
- Büyük olasılıkla kabul edilebilir bir muafiyet. Yine de karar kodla teyit edilmeli:
  `INTERNAL_API` hangi yolları kapsıyor, bu yollar yalnız servis token'ı ile mi erişiliyor?
- Uygunsa Sonar'da gerekçeyle "Reviewed / Safe" işaretle; kuralı kapatarak gizleme.

## Kurallar hatırlatması

- Yeni ihlal ekleme, ihlali kural kapatarak gizleme (`docs-security/mail-service/` borç listesi geçerli).
- `.env` dosyalarını okuma. ENV değişikliği kullanıcı onayıyla.
- Commit/push yok; iş bitince commit mesajı öner (`AGENTS.md` §7).

## Bu taramadaki diğer bulgular (mail'in değil)

- hardware-service: `HardwareDeviceJwtValidator.java:40,45` (S2259, null dönebilir) — Alper.
- social-service: `JwtConfig.java:46,52` (S6831, gereksiz `@Qualifier`) — Umut.
