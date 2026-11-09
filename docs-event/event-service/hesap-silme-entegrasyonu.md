# Event Service — hesap silme entegrasyonu (Auth bağımlılığı)

> Durum: **EKSİK — Event Service tarafında uç yok. Bu iş EVT-01 kapsamına eklenmedi; EVT-01'den bağımsız, açık bağımlılıktır.**
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa

## 1. Auth akışını gerçekten engelliyor mu? — **Evet**

- Auth silme koordinatörü hedef listesindeki **16 servisin tamamı** onaylamadan hesabı `DELETED` yapmaz; `event` listede. (`docs-auth/servis-sozlesmeleri.md` §3.2, §4; `docs-auth/verilen-cevaplar/event-service/bilgilendirme-2026-11-05.md`: "Bu uç yazılmadıkça görev `PENDING` kalır, hesap silme tamamlanmaz." Durum: AÇIK.)
- Event Service'te bu uç yoktur. EVT-01 ile eklenen güvenlik yapılandırması `/internal/**` yollarını bilerek `denyAll` yapar; Auth'un çağrısı bugün `403` alır → onay sayılmaz (sözleşme: `401/403/404/5xx` onay değildir) → görev üstel geri çekilmeyle (1 dk → en çok 6 sa) sonsuza dek yeniden denenir.
- **EVT-01 ile ne değişti:** `events.organizer_id` artık hesap kimliği (kişisel veriye bağlanan REF) tutuyor. Silme ucu yokken bu kayıtlar hesap silindikten sonra da kalır; yani eksiklik artık yalnız "Auth'un beklemesi" değil, aynı zamanda saklanan kişisel veri sorunudur. Bu yüzden belgesiz bırakılmadı.
- **Kullanıcıya etkisi:** Event dahil herhangi bir hedef onaylamadıkça hesap silme tamamlanmaz. (Diğer hedeflerin durumu bu belgenin konusu değildir.)

## 2. Beklenen sözleşme (kaynak: `docs-auth/servis-sozlesmeleri.md` §3, §3.1; `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md` §2–§5)

| Konu | Beklenen |
| --- | --- |
| Uç | `DELETE /internal/v1/accounts/{accountId}` — Gateway'e **açılmaz**; Auth `EVENT_SERVICE_URL` ile doğrudan çağırır |
| Token | `typ=svc+jwt`, RS256 (Auth JWKS), `iss=ltz-auth`, `aud=event-service` (tek değer), `sub=svc:auth-service` (tam eşleşme), `scope=account:erase`, `account_id` = yoldaki `{accountId}` (değilse/yoksa 403), `jti` var, `exp-iat ≤ 60 sn`, `iat` zorunlu; `roles/sid/ver/amr` yok |
| Kullanıcı token'ı | İç uçta **reddedilir**; servis token'ı da kullanıcı uçlarında geçmez |
| Yanıt | Silme/anonimleştirme **bittiğinde** `204`; veri yoksa/zaten silinmişse de `204` (idempotent). `202` yasak. Gövde dönme |
| Süre | 5 sn içinde; çok veri varsa her çağrıda biraz sil, bitmediyse `503` |
| Log | Yalnız `accountId` + sonuç; token/hesap verisi yok |
| Outbox | Kişisel veri taşıyan bekleyen outbox olayları da silinir/anonimleşir (Event'te outbox henüz yok) |

## 3. Event Service'te yapılacaklar (ayrı iş)

1. `SecurityConfig`: `/internal/**` için ikinci `JwtDecoder` (`aud=event-service`, `svc+jwt`, ömür ≤ 60 sn) ve yola göre `AuthenticationManagerResolver`; `DELETE /internal/v1/accounts/*` için `SCOPE_account:erase`. Örnek: community-service `SecurityConfig`/`JwtConfig`/`AccountErasureController`.
2. `AccountErasureController` + servis: `sub == svc:auth-service` ve `account_id == path` denetimi; tek transaction'da silme; `204`.
3. Silme kuralı: **karar KD-3 (aşağıda)**; migration ve yanıt sözleşmesi değişikliği gerektirir.
4. Testler: Social `AccountErasureTest` şablonu + rehber §5'teki zorunlu test tablosu.
5. `docs/api.md` EVENT bölümüne `Internal` kaydı.
6. Bu iş gerçek bir veri sahibi tablo eklendikçe büyür: EVT-08 (`event_participations`), EVT-10 (`participation_penalties`), EVT-20 (`event_poll_votes`), EVT-21 (`event_reminders`) için "hesap silinince ne olur?" PR maddesi olmalıdır.

### KD-3 — Düzenleyicisi silinen hesabın etkinlikleri — **KARAR VERİLDİ (Nisa, 2026-11-09), UYGULANMADI**

**Karar:**
1. Gelecekteki ve aktif etkinlikler iptal edilir (`PLANNED`/`ACTIVE` → `CANCELLED`).
2. Tamamlanan etkinlikler geçmişte korunur.
3. Düzenleyicinin kullanıcı bağlantısı kaldırılır; gösterimde "Silinmiş kullanıcı" yazılır.

**Bu kararın koda yansıması için gerekenler (hiçbiri henüz yapılmadı; ayrı iş):**

| Konu | Not |
| --- | --- |
| Bağlantının kaldırılması | `events.organizer_id` şu an `NOT NULL`. Nullable yapan yeni migration (V112+, V111 değiştirilmez) gerekir; kaldırılan etkinliklerde `organizer_id = NULL`. "Silinmiş kullanıcı" metni veritabanında değil, API/frontend gösteriminde üretilir (`organizerId: null` → istemci bu etiketi gösterir). Yanıt sözleşmesi `organizerId`'yi nullable yapar, `docs/api.md` güncellenir |
| Tamamlananlar | `COMPLETED` etkinliklerde de bağlantı kaldırılır (karar madde 3 tüm etkinlikler için); yalnız kayıt silinmez |
| İptal | `PLANNED`/`ACTIVE` → `CANCELLED` geçişi EVT-16 durum kurallarıyla uyumlu olmalı; `cancelled_reason` (EVT-15) için "organizatör hesabı silindi" gibi sabit, kişisel veri taşımayan bir kod gerekir |
| Katılımcılar | İptal edilen etkinliğin katılımcıları (EVT-08 sonrası) `CANCELLED_BY_EVENT` olmalı ve bildirilmeli (EVT-22). **Bu bağımlılıklar yazılana kadar silme akışı katılımcı tarafını işleyemez** |
| Metin | Başlık/açıklama kişisel veri içerebilir; rehber §3 ("anonim saklama seçersen içerik metni ve yazar REF'i kalmamalı") gereği, geçmişte korunan `COMPLETED` etkinliklerde metnin saklanması **ayrıca onaylanmalıdır** (açık nokta KD-3a; karar tamamlananların "korunması"dır, metnin kişisel veri değerlendirmesi yapılmamıştır) |
| İdempotans | İkinci çağrı `204` (zaten anonim/iptal). Kısmi ilerleme için `503` ile yeniden deneme |
| Auth bilgilendirmesi | Rehber §3: anonim saklama seçilirse seçim servis belgesine yazılır ve Auth sahibine (Kerem) haber verilir — **henüz bildirilmedi** |

## 4. Başka serviste değişiklik gerekiyor mu? — **Hayır**

- Auth: hedef listesinde `event` var; `EVENT_SERVICE_URL` Gateway ile aynı değişken, Compose'ta tanımlı. Servis token'ı Auth tarafından `aud=event-service` ile üretilir. Auth'ta değişiklik gerekmez (`docs-auth/entegrasyon/event-service.md`: "Auth'ta bir şey değişmez").
- Bu nedenle `docs-event/<servis>/talep/req-XX.md` oluşturulmadı. Event'in **ileride** Notification/Mail/Content/Admin ile konuşması (EVT-21, 22, 24) ve `events:ingest` servis istemci çiftleri Auth'ta tanımsızdır; o iş başladığında Auth için `docs-event/auth-service/talep/req-01.md` açılacaktır.

## 5. Kabul ölçütü

Auth'un silme koordinatörü `event` hedefinden `204` alır ve Auth tarafındaki görev `PENDING`'den çıkar; `events` içinde silinen hesaba ait kişisel veri kalmaz. Şu an **hiçbiri sağlanmıyor**.
