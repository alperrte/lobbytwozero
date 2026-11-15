# TASK 4 — Social öneri eligibility sözleşmesi talebi

2026-11-15 · REC-13/14 · Sahip: **Umut**, Auth izinleri **Kerem** · **BLOCKED**.
Bu belge mevcut API'yi genişletmez; öneri tüketicisi için eksik kabiliyeti tarif eder.

## Mevcut kanıt

- `POST /internal/v1/blocks/check`: 1–100 diğer kimlik; iki yönlü engeli tek boolean ile döner.
- `BlocksCheckController` ve `TokenClaims.BLOCKS_CHECK_SUBJECTS` yalnız
  `svc:messaging-service` / `svc:lfp-service` kabul eder. Aynı scope ile başka subject 403.
- Auth mevcut Recommendation hedefleri Library `signals:read` ve Performance `reference:read`;
  Social grant'ı yoktur. Token issuer'ın teknik olarak claim imzalayabilmesi, token grant
  endpoint'inde Recommendation'a bu hedefi kullanma yetkisi verildiği anlamına gelmez.
- `GET /api/v1/social/friends` owner arkadaş listesidir: limit 1–50, cursor, friendId/since.
  Yalnız bu liste ve owner engellenenler listesi B→A engelini kanıtlamaz. Listeyi okumak
  izinli aday keşfi veya bütün adaylar için güncel öneri eligibility kontrolü değildir.

## İstenen en dar kabiliyet

Umut, Recommendation için izinli ve sınırlı batch eligibility sözleşmesini belirlemeli;
Kerem gerekiyorsa dar Auth audience/scope/account binding yetkisini ayrıca onaylamalı.
Burada endpoint/scope adının uygulanmış olduğu iddia edilmez.

- Viewer doğrulanmış kimliğe bağlı; başka hesabın ilişkilerini sorgulama yetkisi verilmemeli.
- Self, mevcut arkadaşlık, A→B veya B→A engel ve erişime kapalı aday dışlanmalı.
- Yanıt tercihen yalnız önerilebilir aday kimliklerini taşımalı; engelin yönü, sahibi, zamanı
  veya özel arkadaş listesi döndürülmemeli. Eksik/atlanmış sonuç izin gibi yorumlanmamalı.
- Gerekliyse pending arkadaşlık isteğinin eligibility etkisi owner tarafından netleştirilmeli;
  Recommendation iş kuralını tahmin etmemeli.
- Boyut sınırı, tekilleştirme, tamlık, kaynak güncelliği ve değişen arkadaşlık/engel/rıza
  durumunda yeniden değerlendirme semantiği tanımlanmalı. Başlangıç talebi tek batch ≤100.
- 401/403/timeout/503 veya belirsiz yanıtta aday döndürülmemeli. N+1 pair sorgusu, cross-DB
  erişimi, başka servisin token'ını kullanma ve caller taklidi yapılmamalı.

## Beklenen owner kabul kanıtı

Self, friend, iki engel yönü, unblock, başkasının ilişkisini sorgulama, servis subject/scope/
account binding, batch sınırı/tamlığı/duplicate ve timeout testleri; gerçek Auth grant + Social
HTTP kabulü ve `docs/api.md` kaydı. Sonrasında Recommendation sıralama ve son aşama privacy
testleri yazılabilir. Mevcut TASK 4 yalnız kapalı erişim sınırını doğrular; bu producer kabulü değildir.
