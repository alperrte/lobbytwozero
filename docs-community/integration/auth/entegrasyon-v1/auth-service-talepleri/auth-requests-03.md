# Auth Talebi 03 — Hesap durumu yayılımı (D3), rol iptal gecikmesi ve JWKS davranışı

> Tarih: 2026-10-27 · Gönderen: Nisa (community-service) · Muhatap: Kerem (Auth) · Durum: **CEVAPLANDI (2026-10-28); `AccountStatusChanged` hâlâ planlı (tarih yok), bkz. `auth-requests-04.md`**
> Not: Aşağıdakiler Auth'un yapısını değiştirme isteği değildir. Mevcut belgelerde **[BEKLEYEN]** işaretli konuların community açısından neden önemli olduğunu ve community'nin bu sırada ne yaptığını açıklar.

## 1. `AccountStatusChanged` (D3) — askıdaki/yasaklı hesabın içeriği

`docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` §7: Hesap durumu claim'de yok; askıdaki/yasaklı hesabın token'ı süresi dolana kadar (en çok 5 dk) geçerli görünür. `AccountStatusChanged` olayı **[BEKLEYEN, D3]**.

**Community açısından etki:** Yasaklanan bir kullanıcının mevcut gönderileri ve yorumları community'de görünmeye devam eder; kullanıcı token'ı süresi dolana kadar yeni içerik de üretebilir. Event kataloğunda (`docs/architecture.md` §6.3) `AccountStatusChanged` auth → community olarak planlı.

**İstenen:** D3 tasarlandığında şunların netleşmesi:
1. Olayın zarfı ve yükü (hesap kimliği, yeni durum: `ACTIVE | SUSPENDED | BANNED | DELETION_REQUESTED ...`).
2. Teslim yöntemi (aynı `POST /internal/events`, aynı servis token'ı modeli — bkz. `auth-requests-01.md`).
3. Community'nin beklenen davranışı: askıda/yasaklıda içerik **gizlensin mi** (HIDDEN), yoksa yalnız yeni içerik üretimi mi engellensin? (Ürün/Auth kararı; community her iki seçeneği de uygulayabilir.)

**Beklerken community:** Hiçbir şey yapmaz; uydurma bir olay tanımlamaz. Yeni içerik üretimi yalnızca geçerli kullanıcı token'ına bağlıdır (Auth, token vermeyi durdurur).

## 2. Rol iptali gecikmesi ve moderatör yetkisi

Community, forum yönetimi (oluşturma/güncelleme/gizleme) için `roles` claim'indeki **`MODERATOR`** ve **`ADMIN`** rollerini kabul eder (öneksiz, tam eşleşme). Rehber §7'ye göre rol iptali (örn. moderatörlükten alma) access token ömrü kadar (varsayılan 300 sn) gecikmeyle ulaşır.

**Soru:** Moderatör/yönetici yetkisi gibi daha yüksek riskli işlemler için Auth'un önerdiği ek bir önlem var mı? (Rehber §7: "[ÖNERİ] yüksek riskli işlemlerde ek önlemi servis kendi tasarlasın".) Community'nin şu anki kararı: ek önlem yok, 5 dk'lık gecikme kabul edildi; forum gizleme geri alınabilir (veri silinmez) olduğu için risk düşük.

**Not:** `MODERATOR`/`ADMIN` için TOTP zorunlu olduğu için (rehber §4) community ayrıca 2FA doğrulamaz.

## 3. JWKS ve anahtar dönüşümü

Community, JWKS'i `AUTH_SERVICE_URL/api/v1/auth/.well-known/jwks.json` adresinden çeker; Spring'in `NimbusJwtDecoder`'ı bunu önbelleğe alır ve **bilinmeyen `kid`** görünce yeniler (test edildi: `JwtConfigTest.keyPublishedAfterStartupIsPickedUpOnNextUnknownKid`).

**Sorular:**
1. Rehber §3.1: "tek aktif anahtar; anahtar rotasyonu yok **[BEKLEYEN]**". Rotasyon gelince eski anahtarla imzalanmış token'lar için JWKS'te iki anahtar bir süre birlikte yayınlanacak mı? (Community her iki durumda da çalışır; yalnız bilgi.)
2. JWKS yanıtında `Cache-Control: no-store` var; community yine de Spring varsayılan önbelleğini (5 dk) kullanıyor. Auth bir önbellek süresi önerir mi?
3. Auth kapalıyken (JWKS alınamadığında) community kullanıcı token'larını doğrulayamaz ve `401` döner (fail-closed). Bu beklenen davranış mı?

> **Cevap (Kerem, 2026-10-28):** `AccountStatusChanged` planlı, tarih yok; olay gelene kadar durum değişince eski token'lar en fazla 90 sn içinde ölür. İçeriği gizlemek community'nin ürün kararı, Auth zorunlu kılmıyor; olay gelmeden gizlemek için güvenilir durum bilgisi yok, olay beklenmeli. JWKS: community önbelleğini en çok 5 dk tutmalı ve bilinmeyen `kid`'de bir kez yeniden çekmeli (mevcut davranışımız uyumlu); Auth anahtar değişiminde eski anahtarı bir süre (en az 90 sn, pratikte > 5 dk) yayınlayabilecek (yeni özellik, dalda).

## 4. Kerem'e iletilecek sorular (özet)

1. D3 (`AccountStatusChanged`) için tarih ve community'nin beklenen davranışı (gizle / yalnız yeni üretimi engelle)?
2. Yüksek riskli moderatör işlemleri için Auth'un ek önerisi var mı?
3. JWKS önbellek süresi ve anahtar dönüşümünde eski anahtarın yayın süresi?

## 5. Etkilenen community kodu (bilgi için)

`infrastructure/config/JwtConfig` (JWKS, doğrulayıcılar), `infrastructure/security/SecurityContextCurrentUser` (`hasModeratorRole`), `application/service/ForumService` (rol kontrolü).
