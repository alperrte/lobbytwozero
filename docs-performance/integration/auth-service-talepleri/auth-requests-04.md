# Auth talepleri 04

Tarih: 2026-10-31 · Kapsam: Faz 4 (PERF-14/15/16/19/21)

Auth Service kaynak koduna dokunulmadı. Aşağıdaki talep Auth sahibine (Kerem) iletilmek üzere yazıldı; Auth kodu yalnız okunarak doğrulandı.

## Yeni talep

### REQ-04-A — Hardware ve Recommendation için Performance servis token'ları (YÜKSEK) — `REQUESTED` / `BLOCKED`

**Gerekçe.** Performance iki salt okunur servis ucu sunar (bkz. `docs/api.md` → PERFORMANCE):

| Uç | Çağıran | Gereken `sub` | Gereken `scope` |
| --- | --- | --- | --- |
| `GET /internal/v1/accounts/{accountId}/performance-summary` | Hardware | `svc:hardware-service` | `summary:read` |
| `GET /internal/v1/accounts/{accountId}/games/{gameId}/performance-reference` | Recommendation | `svc:recommendation-service` | `reference:read` |

Ortak gereksinimler: `typ=svc+jwt`, RS256, `iss=ltz-auth`, **tek** `aud=performance-service`, `jti`, `iat/nbf/exp` (≤ 60 sn), `account_id` = yoldaki `accountId`.

**Doğrulanmış mevcut durum (Auth kaynağı okunarak):** `ServiceTokenIssuer.issue(audience, scope, accountId)` her token'a `sub=svc:auth-service` yazar ve kapsamı `^[a-z]+:[a-z]+$` ile sınırlar. Dolayısıyla:

- Performance konusu doğrulaması (`svc:hardware-service` / `svc:recommendation-service`) **gerçek Auth token'larıyla sağlanamaz** (REQ-01-C ile aynı kök neden).
- İstenen kapsamlar Auth'un desenine uyacak şekilde `summary:read` ve `reference:read` seçildi (iki iki nokta kullanılmadı).

**İstenen.** Auth'un, çağıran servise göre `sub`'u (`svc:<çağıran>-service`) ve izinli (çağıran, hedef audience, kapsam) üçlülerini yapılandırılabilir üretmesi; `performance-service` audience'ı için yukarıdaki iki çifti içermesi. Başka çağıranın başka kapsamla bu uçlara erişememesi Performance tarafında doğrulanır; Auth'un bu üçlü listesini dar tutması beklenir.

**Performance tarafı (yapıldı):** konu + kapsam + hesap bağı doğrulaması, yanlışlarda 403 (kimliksiz/yanlış audience 401). Testler yerel imzalı sözleşme token'ı kullanır; **gerçek Auth entegrasyonu DEĞİLDİR**.

**Sınır.** Hardware ve Recommendation kodu bu fazda değiştirilmedi; tüketici sözleşmesi yalnız Performance tarafında tanımlıdır.

## Faz 1'den süren açık talepler — Faz 4 etkisi

| Talep | Durum | Faz 4 etkisi |
| --- | --- | --- |
| REQ-01-A — cihaz token'ı `aud=performance-service` | BLOCKED | Okuma uçları kullanıcı token'ıyla çalışır; etkilenmez. Ancak okunacak gerçek ölçümün cihazdan gelmesi bu talebe bağlı |
| REQ-01-B — cihaz iptali bildirimi | REQUESTED / kabul edilen risk | Yok |
| REQ-01-C — servis token'ında çağıran konusu | BLOCKED | **REQ-04-A ile aynı kök neden**; ikisi birlikte çözülmeli |
| REQ-01-D — Auth silme işçisi DEV doğrulaması | NOT VERIFIED | Yok |

Ayrıntı: [auth-requests-01.md](auth-requests-01.md), [auth-requests-03.md](auth-requests-03.md).

## Not

Tüm fazlar bittikten sonra bu talepler (auth-requests-01..04) Auth sahibine iletilecek; yanıtlar gelince düzeltmeler yapılacaktır (kullanıcı kararı).
