# Auth — Süreç Borçları: Sıralı Kapanış Planı

> Tarih: 2026-10-24 · Sahip: Kerem · Kapsam: kod dışı, doğrulama ve teslim işleri. Her adım bir öncekine bağlıdır; atlanırsa
> "tamamlandı" denmez. Durum sütunu gerçeği gösterir.

## Sıra

| # | Adım | Nasıl | Kim | Durum |
| --- | --- | --- | --- | --- |
| 1 | **Docker Desktop açık ve yeterli bellekli mi?** | `docker info`; 18 imajlık `full dev` bellek yetersizliğiyle düşmüştü. WSL2 bellek sınırı (`%UserProfile%\.wslconfig` → `memory=`) yükseltilir veya önce yalnız gereken servisler açılır | Kerem | Docker açıldı (7,9 GB); `full dev` için sınır hâlâ düşük |
| 2 | **Auth birim + entegrasyon testleri** | `mvn -pl services/auth-service test` (JDK 25: `JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1`) | Kerem | Yapıldı: bakım turunun testleri geçti (tam koşu sonucu raporda) |
| 3 | **`node ops/generate-auth-dev-keys.mjs` çalıştır** | Otomatik env üretimi artık `JWT_ACCESS_TTL_SECONDS=300` (eski varsayılan) satırını `60`'a çeker; özelleştirilmiş değere dokunmaz. `AUTH_JWT_ADDITIONAL_PUBLIC_KEYS` opsiyoneldir, üretici doldurmaz (`.env.example` rotasyon notu). Ajan `.env` dosyalarına dokunmaz | Kerem | **Bekliyor (tek komut)** |
| 4 | **Commit** (faz faz) | Aşağıdaki önerilen 4 commit; yalnız ilgili dosyalar | Kerem | Bekliyor |
| 5 | **`pre-push service` (auth)** | `.\pre-push\pre-push.cmd service` → `SAFE TO COMMIT` beklenir | Kerem | Bekliyor |
| 6 | **Docker stack: Auth gerçek ENV ile açılıyor mu?** | Yalnız `postgres`, `mailpit`, `auth-service`, `api-gateway` (+ isteğe bağlı `social-service`) ayağa kalkar; Auth açılışta 16 silme hedefinin `*_SERVICE_URL`'ini ve V65/V66 migration'ını doğrular. Yerel `auth_db` V61–V64 ile durur; Flyway V65–V66'yı **ileri** uygular (checksum değişmedi, repair gerekmez) | Kerem | Bekliyor |
| 7 | **Duman testi (elle veya script)** | kayıt → Mailpit kodu → doğrulama → giriş → refresh → JWKS; ban + ban kaldırma; e-posta değiştirme (kod Mailpit'e); silme isteği → onay → **iptal** → yeniden giriş; zayıf parola reddi | Kerem | Bekliyor |
| 8 | **`pre-push full dev` (18 servis)** | Adım 1'deki bellek sınırı ile; 18 servis UP, gitleaks temiz | Kerem | Bekliyor (borç) |
| 9 | **PR: `feature/auth-service` → `main`** | `docker-compose.yml` değişti (yalnız JWT TTL varsayılanı); Gateway kaynağına dokunulmadı. PR metninde ekip paketinden söz edilmez. Push/PR için ayrıca onay | Kerem | Bekliyor |
| 10 | **`chore/security` düzeltmeleri (ayrı PR)** | (a) `CODEOWNERS`'a `api-gateway`, `admin-service`, `notification-service` sahibi ekle (Gateway güven sınırıdır, sahipsiz); (b) `backend/config/coverage-policy.json` içinde `auth-service` ve `api-gateway` `bootstrap` seviyesinde (baseline 1/2/1) — gerçek kapsama göre yükselt, aksi halde kapı Auth testlerini ölçmez; (c) Gateway `application.properties`'te yalnız boş satır silinmiş, zararsız ama Alper onayı gerekir | Kerem + Alper | Bekliyor |
| 11 | **Ekibe haber** | `ekip-uyum-denetimi-2026-10-24.md` bulguları kişiye göre iletilir (Umut, Alper, Nisa, Hamza) | Kerem | Hazır, gönderilmedi |
| 12 | **Belgeler** | `docs/api.md` (parola kuralı, `WEAK_PASSWORD`, ban kaldırma, silme iptali, e-posta değiştirme, token ömrü), `acik-guvenlik-borclari.md`, `servis-sozlesmeleri.md` güncellendi | Kerem | Yapıldı |

## Commit'ler

Güncel ve hunk bazında ayrıntılı plan: [commit-plani-2026-10-24.md](commit-plani-2026-10-24.md) (7 commit: K1–K7; K6 güvenlik düzeltmeleri, K7 belgeler).

## Bilerek yapılmayanlar (ekip/Alper kararı gerekir)

| Konu | Neden |
| --- | --- |
| Admin servisi tarafı (B4 tüketicisi) | Auth tarafı hazır (K5); admin-service yazılınca `servis-sozlesmeleri.md` §3.4 ile bağlanır |
| Ortak (Redis) rate limit (B1) | Gateway kodu ve altyapı, Alper'in alanı |
| Diğer 17 servis pom'unda bağımlılık yamaları (B13) | Başka sahiplerin dosyaları / ortak parent pom |
| İç ağ TLS/mTLS (B18) | Altyapı kararı; `prod` için https zorunluluğu açılışı bloklayabilir |
| Sıfır gecikmeli iptal yayılımı (D3 tam çözüm) | Servislerin Auth'a sorması veya olay altyapısı gerekir; şimdilik 60 sn |
| TOTP anahtar sürümleme ve HMAC anahtarını amaca göre ayırma (S9 kalanı) | Yeni ENV anahtarı ve kalıcı kurtarma kodu özetleri için göç planı; ENV onayı bekliyor |
