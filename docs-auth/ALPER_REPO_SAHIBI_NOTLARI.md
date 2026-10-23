# Alper'e Özel Not: Repo Sahibi Olarak Yapılacaklar ve Kararlar

> Tarih: 2026-10-21 · Hazırlayan: Kerem · Konu: `feature/auth-service` PR'ı öncesi/sonrası senin (repo sahibi) tarafında kalan işler
> Ekip belgeleri: `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md` ve `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`. Güncel D4 / hesap
> silme notları: [`docs-auth/d4-ekip-notlari/`](d4-ekip-notlari/00_OKU_BENI_GONDERIM_PLANI.md) (2026-10-24).

---

## 1. Özet (30 saniye)

- Repo **PUBLIC**. Kimse kodu okuyabilir; bu yüzden geçmiş dahil taradık: **sır, `.env`, özel anahtar yok** (gitleaks tüm dallar, 43 commit temiz).
- Auth + Gateway dev'de çalışıyor ve test edildi. PR'ı senden gözden geçirmeni istiyorum; ortak dosyalara dokundu (aşağıda §2).
- Senin tarafında **GitHub ayarları** ve birkaç **karar** bekliyor (§3, §5). Bunları yalnız sen yapabilirsin.

---

## 2. PR'da bakman gereken yerler (ortak dosyalar)

| Dosya | Ne değişti | Neye dikkat |
| --- | --- | --- |
| `docker-compose.yml` | Auth için 3 zorunlu anahtar, 35 opsiyonel `AUTH_*` aktarımı, Gateway için `GATEWAY_*`, `JWT_AUDIENCE` ortak bloğa | Zorunlu anahtarlar (`:?`) eksikse **herkesin** `docker compose`'u hata verir; çözüm `node ops/generate-auth-dev-keys.mjs` |
| `backend/services/api-gateway/**` | Yeni güvenlik katmanı: CORS, çerez→Bearer + CSRF/Origin, başlık temizliği, Auth hız sınırı | Gateway'e dokunduğumuz için **senin onayın** gerekiyor (ortak servis). 18 test |
| `.env.example`, `.gitignore` | Yeni anahtar blokları; `docs-auth/` yeni dosyalar için ignore | `.env.example`'da gerçek değer yok |
| `docs/SECURITY.md`, `docs/api.md` | Gateway/Auth durumu güncellendi, AUTH endpoint kayıtları | Kanonik belge; yeni standart icat edilmedi |
| `ops/generate-auth-dev-keys.mjs` (+ test) | Yerel Auth anahtarlarını üreten script | Yalnız `node:fs/crypto/path/url`; ağ/süreç yok, bunu bir test zorluyor |
| `backend/services/auth-service/**` | Auth Service (249 test, %93 satır kapsamı) | Migration **V61–V64**; merge sonrası donuk |
| `backend/services/*/Dockerfile` (auth, gateway) | `HEALTHCHECK` | Diğer servis Dockerfile'larına dokunulmadı |

Pre-push kontrolleri geçti: `auth-service` 249 test (%93,17), `api-gateway` 18 test (%94,19), 45 Node testi. Ben kendi PR'ımı merge etmeyeceğim, **sen onaylayınca**.

---

## 3. GitHub ayarları (yalnız sen yapabilirsin, ~10 dakika)

Şu an `main` için dal koruması **yok** (API 404 döndü) ve secret scanning / push protection **görünmüyor**. Public repoda bunlar şart:

| # | Ayar | Nerede | Neden |
| --- | --- | --- | --- |
| 1 | **Secret scanning** + **Push protection** aç | Settings → Code security → Secret scanning | Biri yanlışlıkla `.env`/token pushlamaya kalkarsa GitHub engeller. Public repoda ücretsiz |
| 2 | **`main` için dal koruması / ruleset** | Settings → Branches (veya Rules → Rulesets) → `main` | "Merge öncesi PR zorunlu", "en az 1 onay", "force push yasak", "dal silme yasak" |
| 3 | `ops/`, `.github/`, `pre-push/`, `docker-compose*.yml`, `**/Dockerfile` için **CODEOWNERS** | `.github/CODEOWNERS` dosyası + ruleset'te "Require review from Code Owners" | Bu dosyalar çalıştırılan koddur. Birinin sinsice değiştirmesi (ör. `.env` okuyup dışarı gönderen satır) tüm ekibi etkiler. Örnek: `/ops/ @alperrte` `/pre-push/ @alperrte` `/.github/ @alperrte` `/docker-compose*.yml @alperrte` |
| 4 | Dependabot alerts / security updates | Settings → Code security | Aşağıdaki §4 bağımlılık açıkları için otomatik uyarı |
| 5 | Fork PR'ları için Actions onayı (ileride CI eklenirse) | Settings → Actions | CI'a sır koyacaksak fork'tan çalıştırılmasın |
| 6 | Üyelerin 2FA'sı | Organization/Collaborators | Hesap ele geçirilirse repo da gider |

Not: Bu repoda şu an `.github/workflows` yok; CI eklenirse sırlar yalnız GitHub Secrets'ta durmalı, workflow dosyaları da CODEOWNERS'a girmeli.

---

## 4. Ortak (senin alanın) teknik borçlar

| # | Konu | Durum | Önerim |
| --- | --- | --- | --- |
| 1 | **Bağımlılık açıkları (B13).** Tomcat/Jackson yalnız auth-service'te yamalı (`tomcat 11.0.25`, `jackson 2.21.7 / 3.1.7`). **Gateway dahil diğer modüller eski, CRITICAL/HIGH sürümde** | EKSİK | Parent POM'a (`backend/pom.xml`) aynı property'leri koy ya da Boot yama sürümüne çık. Parent POM ortak dosya, ben dokunmadım |
| 2 | **Ortak Security modülü.** Her servis JWT doğrulayıcıyı ayrı yazarsa kopya olur | EKSİK | Küçük ortak modül: `NimbusJwtDecoder` (JWKS), `iss/aud/typ/exp` doğrulayıcıları, `roles` → `ROLE_*` dönüştürücü. Tasarım hazır, `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` §3.4 |
| 3 | **Servis kimliği (D4).** `svc+jwt`: `sub=svc:<çağıran>`, `aud=<hedef>`, `scope`, `exp ≤ 60 sn` | SÖZLEŞME | Ortak Security ile birlikte; Admin→Auth ve Auth→servis silme çağrıları buna bağlı |
| 4 | **Gateway hız sınırı çok örnekli değil** (bellek içi) | EKSİK | Prod'da birden çok Gateway olacaksa Redis tabanlı limitleyici |
| 5 | **Prod dağıtımı tasarlanmadı:** sır yöneticisi, servis başına DB rolü (şu an hepsi `ltz_dev` superuser), prod compose/ortam | EKSİK | Prod öncesi zorunlu. Auth `prod` profili ayrıca Mail Service adaptörü olmadan bilerek açılmıyor |
| 6 | **Placeholder parola koruması.** `.env.example`'daki `CHANGE_ME` değerleriyle prod'a çıkılırsa | KORUMA YOK | İstersen Auth'ta "prod'da `CHANGE_ME` ile başlama" kontrolü eklerim |
| 7 | **Bilinen güvenlik borçları herkese açık dosyada** (`docs-auth/acik-guvenlik-borclari.md` izlenen dosya) | Public | Prod yaklaşınca ayrıntılı listeyi özel bir yere alalım; repoda yalnız özet kalsın |
| 8 | **Commit yazar e-postaları** public geçmişte görünüyor (herkesinki) | Bilgi | Herkes `git config user.email` için GitHub noreply adresi kullanabilir (`ID+kullanici@users.noreply.github.com`). Geçmişi yeniden yazmayı önermem |

---

## 5. Senden beklenen kararlar

| # | Karar | Kimi etkiler |
| --- | --- | --- |
| 1 | **PR'ı onaylar mısın?** Ortak dosyalar (compose, Gateway, `.env.example`, `SECURITY.md`) §2'de | Herkes |
| 2 | **GitHub ayarları** (§3: secret scanning, `main` koruması, CODEOWNERS) | Herkes |
| 3 | **Performance servisi için cihaz kimliği (PERF-01).** Auth'ta cihaz token'ı yok. `SECURITY.md` cihaz token'ının kullanıcı oturum token'ından ayrı olmasını söylüyor. Seçenek A: Auth'a ayrı bir "cihaz kimliği" ekleriz (küçük bir tasarım işi, benim tarafımda). Seçenek B: Performance kendi cihaz eşleştirmesini yapar, Auth yalnız kullanıcıyı doğrular | Senin servisin (performance) |
| 4 | **Ortak servisler (admin, notification) sorumlusu kim?** `AGENTS.md` "ortak" diyor. Auth'un admin uçlarını çağıran Admin Service ve silme hedefi olan Notification için bir sahip lazım | Admin/Notification |
| 5 | **Silme hedef listesi.** Listede `game` ve `admin` yok. `accountId` tutan veri varsa eklenmeli | Hamza + admin |
| 6 | **Hukuki/ürün kararları (hepsi bekliyor):** güvenlik olayı saklama süresi (B9), silme sonrası geri alma süresi (B17), silmede Auth PII'sinin ne zaman anonimleştirileceği (S1) | Kerem (Auth) |
| 7 | **İlk ADMIN nasıl oluşacak?** Şu an elle SQL ile rol verilir (B7) | Yönetim akışı |
| 8 | **Parola politikası:** en az 12 karakter, kara liste yok. NIST önerisi ≥15 + kara liste | Auth + ürün |

---

## 6. Kısa sonuç

- Kod ve geçmiş temiz; Auth + Gateway dev için hazır.
- Senden: **PR onayı**, **GitHub korumaları (§3)**, **§4.1 parent POM bağımlılık yamaları** ve **§5 kararlar**.
- Bu belgeyi (ve içindeki GitHub ayarı eksiklerini) repoya koyma, yalnız kendinde tut.
