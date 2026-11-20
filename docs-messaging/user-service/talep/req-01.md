# User Talebi 01 — Kullanıcı varlığı ve hesap durumu için iç uç (Messaging, Social, LFP ortak ihtiyacı)

> Tarih: 2026-11-20 · Gönderen: Umut (messaging-service; aynı ihtiyaç social-service ve lfp-service için de geçerli) → `user-service` (Kerem)
> Durum: **TALEP / ÖNERİ — User'da uygulanmadı.** "İstenen sözleşme" öneridir; bugün User'da bulunan bir uç değildir.
> Gereksinimler: **MSG-27**, **SOC-15**, **LFP-18** (`user-service` ile sözleşme bazlı entegrasyon).
> Social tarafı: [docs-social/user-service/talep/req-01.md](../../../docs-social/user-service/talep/req-01.md) (bu sözleşmeye bağlanır).

## 1. İhtiyaç ve kullanım senaryosu

Messaging, birebir sohbet açılırken (`POST /api/v1/messaging/conversations/direct {userId}`) ve gruba üye eklenirken hedef kimliğin **gerçek ve etkileşime açık bir hesap** olup olmadığını doğrulamıyor. Bugün silinmiş ya da hiç olmamış bir `userId` ile sohbet açılabiliyor; sohbet "ölü üyeli" kalıyor ve mesajlar boşa gidiyor. Bu, `docs-messaging/TASK-08.md` §6 ve §7 madde 3'te "tüm servislerde ortak açık nokta" olarak kayıtlı.

Aranan bilgi yalnızca "bu kimlik var ve görünür mü" olup profil içeriği değildir.

## 2. Karşı servisin mevcut desteği ve eksik nokta

Doğrulanan durum (2026-11-20, `origin/main`; `user-service` iç uçları):

| Uç | Amaç | Çağıranlar |
| --- | --- | --- |
| `POST /internal/v1/privacy/decisions` | Alan bazlı gizlilik kararı (`FRIENDS_LIST`, `LIBRARY`, `REVIEW_AUTHOR`, …) | social, hardware, library (`privacy:read`) |
| `POST /internal/events` | Olay girişi | social, library, review, auth |
| `GET /internal/v1/users/{userId}/locale` | Dil tercihi | auth |
| `DELETE /internal/v1/accounts/{accountId}` | Hesap silme | auth |

- Kullanıcı varlığını/durumunu döndüren iç uç **yoktur**; `privacy/decisions` çağıranları social/hardware/library ile sınırlıdır ve alan listesi varlık sormaz.
- Kullanıcı token'ını servisler arası iletip `profiles/batch` çağırmak kural dışıdır (servis token'ı ile kullanıcı token'ı karışmaz).
- Auth'ta `messaging-service → user-service` çifti **tanımlı değil**.

**Eksik:** Messaging'in (ve Social, LFP'nin) kimlik doğrulaması için çağırabileceği, fail-closed çalışan bir iç uç.

## 3. İstenen sözleşme (ÖNERİ)

Toplu sorgu, Social'ın `blocks:check` kalıbına benzer; yalnız durum döner, profil verisi dönmez.

`POST {USER_SERVICE_URL}/internal/v1/users/status` (Gateway'e açılmaz)

**Kimlik doğrulama (User doğrular):** `typ=svc+jwt`, `aud=user-service`, `scope=users:status:read` (yeni kapsam adı önerisi; Kerem farklı bir ad seçebilir), `sub=svc:messaging-service` | `svc:social-service` | `svc:lfp-service`. Kullanıcı token'ı kabul edilmez.

```json
// İstek
{ "userIds": ["UUID", "..."] }          // 1..100, tekrarsız
// Yanıt 200
{ "results": [ { "userId": "UUID", "status": "ACTIVE | UNAVAILABLE" } ] }
```

- `ACTIVE`: hesap var, silinmemiş ve etkileşime açık. `UNAVAILABLE`: yok, silinmiş (`DELETION_REQUESTED` dahil) ya da askıda/yasaklı. Çağıran tarafın nedeni ayırt etmesi gerekmez; neden sızdırılmaz.
- Profil olmayan ama Auth'ta var olan hesap için karar User'ın: profil oluşturma gecikmesi bu uçta `UNAVAILABLE` sayılırsa yeni kayıtlar kısa süre sohbet açamaz. Bu davranışı Kerem belirlesin (profil dışı kaynağı ya da "var" saymak). **Açık soru.**
- Gövde > 100 kimlik veya geçersiz UUID: `400`; kimlik `401`; kapsam/`sub` `403`; hata `ProblemDetail`.

**Çağıran davranışı (Messaging'de uygulanacak):** `ACTIVE` olmayan hedefe birebir sohbet/üyelik `404` (hedefin varlığını sızdırmayan genel hata); User'a ulaşılamazsa **fail-closed** `503` (yeni sohbet açılmaz; mevcut sohbetlerde mesajlaşma etkilenmez).

## 4. Messaging tarafında hazırlık ve bekleyen iş

- Mimari hazır: dış çağrı kalıbı `SocialBlockCheckClient` ile aynı (`ServiceTokenPort`, fail-closed). Yeni port + istemci küçük bir iş; uç ve Auth çifti gelince yapılır.
- Bu uç gelene kadar Messaging'de değişiklik yok.
- Askı/yasak sonrası mevcut hesapların etkileşimi için ayrıca `AccountStatusChanged` olayı katalogda (§6.3) vardır; bu talep onu kapsamaz (yalnız yeni etkileşimde senkron kontrol).

## 5. Kabul kriterleri

1. Var olan aktif kullanıcı için `ACTIVE`, silinmiş/olmayan için `UNAVAILABLE` döner.
2. 101 kimlik, geçersiz UUID, kullanıcı token'ı, yanlış `aud`/`scope`/`sub` reddedilir.
3. Dev'de gerçek Auth çifti ile Messaging, olmayan kimlikle birebir sohbeti `404` ile reddeder; User kapalıyken `503`.
4. Yanıtta profil verisi veya hesap durumunun nedeni yoktur.
5. `docs/api.md` USER bölümüne uç kaydedilir.
