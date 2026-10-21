# LTZ-KER-AUTH-SEC-01 — Hesap Güvenliği ve Veri Yaşam Döngüsü (Kapanış)

Kapsam: yalnız `auth-service`, `docs/api.md` AUTH bölümü ve `docs-auth/`. Gateway, parent POM ve ortak Security modülüne dokunulmadı.

## Kapatılan bulgular

| ID | Durum | Çözüm |
| --- | --- | --- |
| N1 hesap ön-ele-geçirme | KAPANDI | **Kanıt bağlama**, kimlik bilgisi üzerine yazma yok. E-posta onayı kaydı yapan tarafın kanıtını ister: parola (parola kaydı) veya SteamID'ye bağlı, tek kullanımlık, amaç-ayrımlı `verificationProof` (Steam kaydı, `steam_registrations.purpose`, V69). Yanlış kanıt kod denemesini tüketmez; yanıt tüm hatalarda aynı (hesap varlığı sızmaz). Bekleyen Steam hesabıyla giriş `status=email_verification_required&proof=` döner. |
| N3 bekleyen kayıtlar | KAPANDI | Silme onayında sıfırlama/doğrulama kodu, MFA challenge ve hesabın Steam kanıtları geçersiz kılınır; tam silmede `steam_registrations` de temizlenir (kalıcı PII silme sırası korunur). |
| N4 takılı silme | KAPANDI | Eksik servis başarı sayılmaz. Görev `ltz.auth.deletion.stuck-attempts` (10) denemede "takılı" olur: tek `WARN` günlüğü + yönetici uç noktası `GET /api/v1/auth/admin/account-deletions/pending`. Başarısızlık olayı yalnız 1. denemede ve eşikte yazılır. |
| N5 / B9 temizlik | KISMEN | `ltz.auth.cleanup.*` ile yapılandırılabilir, sınırlı partili, çoklu örnekte güvenli (SKIP LOCKED / idempotent) temizlik: OAuth akışları, nonce, Steam kanıtları, deneme sayaçları, süresi dolan kod/token/MFA/oturum, **24 saati geçen doğrulanmamış hesaplar (silinmez, anonimleştirilir; denetim olayları korunur)**. Açılışta tutarlılık denetimi: nonce saklama ≥ 2× Steam assertion yaşı; sayaç saklama ≥ pencere + kilit süresi. `security_events` için süre **tanımsız → silme yok**. |
| Kimliksiz kaynak tüketimi | AZALTILDI | Hesapsız (`account_id is null`) güvenlik olayı artık tabloya yazılmaz; kilitliyken istek başına `ATTEMPTS_THROTTLED` yazılmaz (kilit anında tek `WARN`); Argon2 slot bekleme süresi 3 sn → 0,5 sn. IP bazlı sınır hâlâ yok (B1, Gateway). |

## Karar bekleyen konular (uygulanmadı)

1. **Auth PII anonimleştirme zamanı:** silme onayında hemen mi, geri alma süresi sonunda mı, tüm servisler onaylayınca mı (şimdiki). Geri alınamaz; B17 ile birlikte karar.
2. **`security_events`, durum ve rol geçmişi saklama süresi:** hukuki süre kararı gelene dek silinmez (`security-event-retention` boş).
3. **`pending-account-ttl`** (24 saat varsayım) onayı.
4. **Silme hedef servis listesi** (14) ve verisi olmayan servis için "uygulanamaz" işaretleme yolu.

## Yapılandırma (yeni anahtarlar, hepsi `application.properties` varsayılanlı, gizli değer yok)

`ltz.auth.cleanup.{enabled,interval,batch-size,pending-account-ttl,flow-grace,nonce-retention,throttle-retention,token-retention,session-retention,security-event-retention}`, `ltz.auth.deletion.stuck-attempts`, `ltz.auth.oauth.steam.verification-proof-ttl`. ENV anahtarı eklenmedi.

## Migration sadeleştirmesi

Henüz hiçbir yere push edilmediği için auth migration'ları ALTER içermeyen **4 temiz dosyaya** indirildi (son şema aynı, `purpose`, `first_factor`, `token_version`, `request_id` ve rol seed'i doğrudan CREATE/INSERT içinde):

| Dosya | İçerik |
| --- | --- |
| `V61__create_account_tables.sql` | hesap, roller (5 kanonik seed), güvenlik olayları, durum/rol geçmişi |
| `V62__create_session_and_credential_tables.sql` | oturum, refresh token, deneme sayacı, doğrulama kodu, parola sıfırlama, TOTP, kurtarma kodu, MFA challenge |
| `V63__create_external_identity_tables.sql` | harici kimlik, OAuth akışı, nonce, Steam kayıt/kanıt token'ı |
| `V64__create_account_deletion_tables.sql` | silme token, talep, görev |

**V65–V70 boş.** Yerel geliştirme veritabanları (Flyway sağlama toplamı değiştiği için) sıfırdan oluşturulmalıdır; Testcontainers etkilenmez.
