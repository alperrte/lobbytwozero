# Admin Talebi 01 — `ContentReported` alıcısı ve `ModerationActionTaken` göndericisi

> Tarih: 2026-11-07 · Gönderen: Nisa (community-service) → `admin-service`
> Sorumlu geliştirici: **atanmamış.** Admin ortak servistir (`AGENTS.md` §1, `docs-security/admin-service/NOTLAR.md`); belgelerde bir sahip yazılı değil. Değişiklikten önce sorumluluğun kimde olduğu belirlenmelidir.
> Durum: **TALEP / ÖNERİ — Admin'de uygulanmadı.** Aşağıdaki "İstenen sözleşme" bölümü öneridir; bugün Admin'de bulunan bir uç ya da olay değildir.
> Community gereksinimleri: **COM-16** (gönderi ve yorum raporlama), **COM-17** (moderasyon entegrasyonu), **COM-21** (servis sınırları).

## 1. İhtiyaç ve kullanım senaryosu

1. Oyuncu uygunsuz bir gönderiyi ya da yorumu raporlar (`POST /api/v1/community/posts/{id}/reports`, `.../comments/{id}/reports`). Community raporu kendi veritabanına yazar ve **aynı transaction'da** outbox'a `ContentReported` olayı bırakır.
2. Admin olayı alır, moderasyon kuyruğuna (vaka) alır. Moderatör vakayı inceler ve karar verir (gizle, geri aç, kaldır, reddet).
3. Admin kararı Community'ye `ModerationActionTaken` olayıyla iletir. Community kararı içeriğe uygular ve ilgili açık raporları sonuçlandırır.
4. Karar kim tarafından, neden ve ne zaman verildi bilgisi **Admin'de** (denetim kaydı) tutulur; Community yalnız içerik durumunu değiştirir.

Gereksinim belgesi: "Moderatör bekleyen raporları listeler; rapor ve bağlı içeriği inceler; uygun işlemi seçer; rapor durumu güncellenir; moderasyon eylemi audit loga yazılır."

## 2. Karşı servisin mevcut desteği ve eksik nokta

Doğrulanan durum (2026-11-07):

- `backend/services/admin-service` içinde yalnız `AdminServiceApplication` ve `AdminServiceApplicationTests` vardır. Controller, servis, entity, migration, Auth doğrulayıcısı ve RestClient yoktur.
- `docs/api.md` ADMIN bölümü: `_Kayıt yok._`
- `docs/architecture.md` §6.3 `ContentReported` (üretici community dahil → admin) ve `ModerationActionTaken` (admin → kaynak servis) olaylarını yalnız **[ÖNERİ]** olarak listeler; §6.4 moderasyon sagasını `PENDING_APPLY` durumuyla tarif eder.
- Auth tarafı hazırdır (`docs-auth/entegrasyon/community-service.md`, `admin-service.md`): `community-service → admin-service` ve `admin-service → community-service` çiftleri `events:ingest` kapsamıyla tanımlıdır. Admin'in kendi istemcisi `AUTH_ADMIN_SERVICE_CLIENT_SECRET` ile açılır.
- Aynı alıcı modeli Review (`docs-review/request/admin-review-integration.md`) ve Content (`docs/api.md` CONTENT, "Admin Service → Content Service") tarafından da bekleniyor. Üç servisin zarfı ve alma kuralları ortaklaştırılmalıdır.

**Eksik:** Admin'de olay alıcısı yoktur, bu yüzden Community'nin raporları teslim edilemez. Admin'de karar gönderen taraf da yoktur, bu yüzden Community'nin `ModerationActionTaken` alıcısı (hazır) gerçek bir üreticiyle denenememiştir.

## 3. İstenen sözleşme (ÖNERİ)

### 3.1 Community → Admin: `ContentReported` (Admin'de yazılacak alıcı)

`POST {ADMIN_SERVICE_URL}/internal/events` (Gateway'e açılmaz; `/internal/**` route edilmez).

**Kimlik doğrulama (Admin doğrular):** Auth'un servis token'ı — `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=admin-service`, `scope` ⊇ `events:ingest`, `sub=svc:community-service`, `jti`, ömür ≤ 60 sn. Zarftaki `producer` değeri `sub` ile (`svc:<producer>`) birebir eşleşmelidir. Kullanıcı token'ı (`at+jwt`) bu uçta kabul edilmez.

**Zarf** (`docs/architecture.md` §6.2; Community bugün tam bu biçimi gönderiyor):

```json
{
  "eventId": "UUID (outbox kaydı kimliği; yeniden teslimde aynı kalır)",
  "eventType": "ContentReported",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC",
  "producer": "community-service",
  "aggregateId": "UUID (reportId)",
  "payload": {
    "reportId": "UUID",
    "targetType": "POST | COMMENT",
    "targetId": "UUID",
    "targetAuthorId": "UUID",
    "reporterId": "UUID",
    "reason": "SPAM | HARASSMENT | HATE_SPEECH | SEXUAL_CONTENT | VIOLENCE | ILLEGAL_CONTENT | MISINFORMATION | OTHER",
    "detail": "string | null (en çok 500 karakter, düz metin)",
    "reportedAt": "ISO-8601 UTC"
  }
}
```

- Yük yalnız kimlik, sabit gerekçe ve isteğe bağlı serbest metin taşır; gönderi/yorum metni taşımaz (içeriği görme ihtiyacı için `req-02.md`).
- Community aynı (raportör, hedef) çifti için tek rapor üretir (veritabanı UNIQUE). Her `reportId` benzersizdir.

**Alıcı kuralları (Admin'den beklenen):**

1. Kalıcı kabul: vaka/inbox kaydı **commit edildikten sonra** `200` döner. Kayıt yapılmadan `2xx` dönülmez; Community `2xx` gelen olayı teslim edilmiş sayar ve bir daha göndermez.
2. Tekilleştirme: aynı `eventId` ve aynı `reportId` ikinci vaka, kayıt ya da bildirim üretmez; tekrar teslim yine `200` (`outcome: DUPLICATE`) döner. Aynı `reportId`'nin farklı içerikle gelmesi `409` olur.
3. Yanıt gövdesi Community'nin kendi alıcısıyla aynı biçimde olabilir: `{ "eventId": "UUID", "outcome": "PROCESSED | DUPLICATE | IGNORED" }`. Community yalnız durum kodunu kullanır.
4. Bilinmeyen `eventType` `200 IGNORED` döner (üretici sonsuz yeniden denemesin).
5. Yanıt süresi: Community okuma zaman aşımı 5 sn (`spring.http.clients.read-timeout`); ağır iş eşzamanlı yapılmamalı.
6. Hata davranışı: `400` geçersiz zarf/yük, `401` kimlik yok/geçersiz, `403` kapsam ya da `sub`≠`producer`, `409` çelişkili aynı kimlik, `413` aşırı gövde, `429/503` geri basınç. Community **her 2xx dışı yanıtı** (3xx dahil, yönlendirme izlenmez) yeniden dener: 10 sn × 2ⁿ üstel bekleme (en çok 1 saat), en çok 10 deneme, sonra `FAILED` (el ile müdahale). Bu nedenle kalıcı hata (`400`) ile geçici hatanın ayrımı Admin'de loglanmalıdır.

### 3.2 Admin → Community: `ModerationActionTaken` (Community alıcısı **hazır**; Admin'de gönderici yazılacak)

Community tarafı `docs/api.md` COMMUNITY → "Servisler Arası Olay Girişi" kaydındadır. Özet:

`POST {COMMUNITY_SERVICE_URL}/internal/events`; token Auth'tan `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `{clientId: "admin-service", clientSecret, audience: "community-service"}` karşılığı alınır (`scope=events:ingest`, `sub=svc:admin-service`, 60 sn). Her çağrıda ya da bitimden önce yenilenen token kullanılır.

```json
{
  "eventId": "UUID (karar kimliği; yeniden denemede AYNI kalmalı)",
  "eventType": "ModerationActionTaken",
  "eventVersion": 1,
  "occurredAt": "ISO-8601 UTC (karar zamanı)",
  "producer": "admin-service",
  "aggregateId": "UUID (vaka veya karar kimliği)",
  "payload": {
    "targetType": "POST | COMMENT",
    "targetId": "UUID",
    "action": "HIDE | RESTORE | REMOVE | DISMISS",
    "reportId": "UUID (isteğe bağlı; yalnız tek raporu hedeflemek için)"
  }
}
```

Davranış (Community'de uygulanmış ve testli): `HIDE` görünür içeriği gizler; `RESTORE` gizli içeriği geri açar; `REMOVE` kalıcı kaldırır (**geri alınamaz**, silinmiş içerik `RESTORE` ile dönmez); `DISMISS` içeriğe dokunmaz, raporu reddeder. `HIDE`/`REMOVE` açık raporları `RESOLVED`, `DISMISS` `DISMISSED` yapar. Olay `eventId` ile tekilleştirilir; bulunamayan içerik için olay başarıyla işlenir (etkisiz).

**Admin'den beklenenler:**

1. **Yetki Admin'de doğrulanır.** Community kararı veren insanı görmez ve doğrulamaz; token yalnız `svc:admin-service` olduğunu kanıtlar. Admin, kararı veren kullanıcının güncel `MODERATOR`/`ADMIN` rolünü kendi sınırında denetler ve denetim kaydına (kim, neden, ne zaman) yazar.
2. **Yeniden denemede aynı `eventId`.** Zaman aşımı ya da 5xx sonrası aynı karar aynı `eventId` ile gönderilir; yeni kimlik üretmek kararı iki kez uygular.
3. **Sıra.** Community `ModerationActionTaken` için olay sırası işareti tutmaz (yalnız engelleme olaylarında vardır). Admin aynı hedefin kararlarını **sıralı** iletmelidir: önceki karar `2xx` ile kabul edilmeden aynı hedef için sonraki karar gönderilmez. Alternatif (öneri): Admin `occurredAt` değerini karar zamanı olarak koyar ve Community engelleme olaylarındaki gibi çift başına son-yazan-kazanır kuralını moderasyona da uygular — bunun için Community'de yeni bir migration ve değişiklik gerekir; Admin sahibiyle birlikte karar verilirse yapılır (şimdi yapılmadı).
4. **Sonuç eşlemesi.** `200` = Community olayı kabul etti (`PROCESSED`/`DUPLICATE`/`IGNORED`). Community, içerik bulunamadığında da `PROCESSED` döner; "gerçekten uygulandı" ile "etkisiz" ayrımı yanıtta yoktur. Admin'in vaka durumunda (`PENDING_APPLY` → uygulandı) bu ayrımı gerekli görüp görmediği **açık sorudur**; gerekiyorsa Community'ye yanıtta ek bir `applied` alanı istenmelidir.
5. Hata yönetimi: `400` (geçersiz yük; yeniden denemek yararsız) → vaka `REJECTED`/uyarı; `401/403` → token yenile, sürerse yapılandırma alarmı; `5xx`/zaman aşımı → aynı `eventId` ile üstel bekleme.
6. Admin hesabı silme ucu: Auth `DELETE /internal/v1/accounts/{accountId}` çağrısı Admin'de de bekleniyor (`docs-auth/verilen-cevaplar/bilgilendirme-2026-11-05-auth-degisiklikleri.md`); yazılmadıkça Auth tarafında Admin görevi `PENDING` kalır. Bu talebin parçası değildir, bilgi olarak anılır.

## 4. Community tarafında tamamlanan hazırlık ve bekleyen iş

Tamamlanan (kod + test):

- `ContentReported` rapor kaydıyla aynı transaction'da outbox'a yazılır (`ReportService`); raporlama kuralları (görünürlük, kendi içeriğini raporlayamama, mükerrer rapor, saatlik 20 sınırı) uygulanır.
- Outbox relay: `for update skip locked` ile kiralama, üstel geri çekilme, 10 denemede `FAILED`, bozuk yükte anında `FAILED`; teslim `EventDeliveryClient` ile hedefe bağlı Auth servis token'ıyla (`aud=admin-service`, `Authorization: Bearer`) yapılır. Gizli anahtar (`AUTH_COMMUNITY_SERVICE_CLIENT_SECRET`) boşsa hiçbir olay denenmez ve deneme hakkı yanmaz.
- `ModerationActionTaken` alıcısı: `events:ingest` kapsamı, `typ=svc+jwt`, `aud=community-service`, `sub` = `svc:admin-service`; `eventId` tekilleştirmesi; etkiler ve sayaçlar gerçek PostgreSQL ile testli.

Bekleyen:

- Relay varsayılan **kapalıdır** (`ltz.outbox.relay.enabled=false`). Açılmadan önce: Admin alıcısı yayında, Auth'ta `community-service → admin-service` çifti ve `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` dev ortamında tanımlı olmalıdır. Admin yokken açılırsa bekleyen `ContentReported` olayları 10 denemede `FAILED` olur ve el ile geri alınması gerekir.
- Gerçek Admin ile uçtan uca doğrulama **yapılmadı (NOT VERIFIED)**.

## 5. Kabul kriterleri

1. Dev'de gerçek Auth token'ıyla bir raporlama isteği sonrası Community relay'i `ContentReported` olayını Admin'e `200` ile teslim eder; Admin'de **tek** vaka oluşur; olay yeniden gönderilirse (`DUPLICATE`) ikinci vaka oluşmaz.
2. Yanlış `aud`, `scope`, `sub`≠`producer` ve kullanıcı token'ı Admin alıcısında reddedilir; Admin yetkisiz olay için vaka açmaz.
3. Admin kesintisinde Community olayı kaybetmez; Admin geri geldiğinde olay teslim edilir ve vaka tek kalır.
4. Yetkili moderatör vakada `HIDE` kararı verir; Admin `ModerationActionTaken` gönderir; Community'de içerik herkese kapanır, rapor `RESOLVED` olur; aynı olayın tekrarı içeriği ve sayaçları değiştirmez.
5. `RESTORE` gizli içeriği geri açar; `REMOVE` sonrası `RESTORE` içeriği geri getirmez.
6. Yetkisiz insan (moderatör olmayan hesap) Admin'de karar veremez; Community'ye hiç olay gitmez.
7. Aynı hedef için ardışık kararlar (ör. `HIDE` sonra `RESTORE`) sırası bozulmadan uygulanır (§3.2 madde 3'teki yöntemlerden biri seçilmiş ve test edilmiş olmalı).
