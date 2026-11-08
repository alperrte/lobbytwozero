# Auth Experience V2: Google ve Steam ilk giriş kararları

Durum: **karar bekliyor.** Bu belge kodu değiştirmez; hangi isteğin yalnız arayüzle çözülemediğini ve neden olduğunu söyler.
Kaynak: `GoogleLoginService`, `SteamLoginService`, `LoginService.complete`, `ExternalLoginController` (okundu, 2026-11-08).

## Bugünkü davranış (koddan doğrulandı)

| Durum | Ne oluyor |
|---|---|
| Bağlı Google/Steam kullanıcısı | Parola girişiyle aynı kurallar: hesap durumu, 2FA ve zorunlu rol kuralı. Oturum açılır, ön yüz `/` sayfasına gider. |
| İlk kez Google | Hesap **hemen** açılır: e-posta Google'dan (doğrulanmış), kullanıcı adı sunucuda üretilir (`g_` + 12 karakter), PLAYER rolü, parolasız. Kullanıcıya kullanıcı adı sorulmaz. |
| İlk kez Steam | Oturum yok. Kısa ömürlü kayıt token'ı döner; ön yüz kullanıcı adı **ve e-posta** ister; hesap `PENDING_VERIFICATION` açılır, e-posta doğrulanınca ACTIVE olur. |
| Google e-postası zaten kayıtlı | Sessiz birleştirme yok: `ACCOUNT_LINK_REQUIRED`. Doğru, korunuyor. |

## İstek 1: İlk Google girişinde kullanıcı adı sorulsun

Ön yüzle çözülemez. Hesap, kullanıcı adı sorulmadan önce sunucuda zaten oluşuyor. Sahte bir form, adı sonradan değiştirmek olurdu ve "oturum açık ama profil eksik" ara durumu yaratırdı.

Önerilen sözleşme (Steam akışının aynısı):
1. Bağsız `sub` + doğrulanmış e-posta + kayıtsız e-posta: hesap **açılmaz**; `registration_required` ile tek kullanımlık, hash'li, kısa ömürlü kayıt token'ı döner (`provider=GOOGLE`).
2. Yeni uç: `POST /api/v1/auth/oauth/google/complete` gövde `{ token, username }`. Hesap burada, tek işlemde açılır; e-posta ve `sub` token kaydından alınır (istemciden gelmez).
3. Kullanıcı adı: mevcut `uq_accounts_username_normalized` kısıtı yarışı çözer; çakışmada 409 `USERNAME_TAKEN` (Steam'deki gibi).
4. Veri: `steam_registrations` tablosu genelleştirilir (`provider`, `subject`, `email` sütunları) ya da ayrı tablo. Migration gerekir (Auth numara aralığında yalnız V70 boş).
5. Ön yüz: `AuthCallback` içinde `registration_required` zaten var; Google için kullanıcı adı alanı (e-posta alanı yok) eklenir. Küçük iş.

Eski davranış (`g_xxx`) kullanılan hesaplar etkilenmez. Test: yarış (iki eşzamanlı tamamlama), süresi dolmuş/tekrar kullanılan token, mevcut e-posta çakışması, rol/2FA kuralının tamamlama sonrası aynı uygulanması.

Karar: **Kerem (Auth sahibi) onayı + migration.** Ürün açısından risk düşük.

## İstek 2: Steam'de e-posta sorulmasın, SteamID esas alınsın

**BLOCKED_BY_PRODUCT_DECISION.** Standart Steam OpenID e-posta vermez; yalnız SteamID64 doğrulanır (bu kısım bugün de böyle).
E-postasız hesap şu bağımlılıkları kırar:

- `accounts.email` zorunlu ve benzersiz; kayıt sonrası e-posta doğrulaması ACTIVE olmanın tek yolu.
- Parola sıfırlama, hesap silme onayı, silme iptali, güvenlik bildirimleri e-postayla gider.
- Hesap kurtarma: Steam erişimi kaybedilirse (hesap çalındı/kapandı) başka yol kalmaz.
- Mevcut e-posta hesabıyla birleştirme politikası (doğrulanmamış e-posta bağlama yok) e-postasız hesapta dayanak kaybeder.
- 2FA: Steam hesapları için `require-for-steam-accounts` (prod'da açık) e-postasız hesapta TOTP kurulumuna kadar oturum vermiyor; bu aynen kalabilir.

Seçenekler:
- **A (bugün):** E-posta zorunlu kalır. Ön yüzde yalnız kopya/açıklama iyileştirilebilir ("e-posta yalnız doğrulama ve kurtarma için"). Güvenlik değişmez. **Önerilen.**
- **B:** SteamID-only hesap; `email` null olabilir, doğrulama atlanır. Gerekenler: `accounts.email` nullable + benzersizlik kısmi indeks, bildirim ve kurtarma kuralı (kurtarma kodları zorunlu?), silme onayı için alternatif kanal, mevcut hesaplar için migration ve geri alma planı, güvenlik onayı.
- **C:** SteamID-only ile başla, e-postayı sonradan isteğe bağlı bağlat (bağlayınca doğrulanır). B'nin kapsamı + "e-postasız hesap sınırları" ürün kararı.

Bu karar verilmeden e-posta zorunluluğu kaldırılmadı ve ön yüz Steam formundan e-posta alanı çıkarılmadı.

## Bu işte yapılanlar / yapılmayanlar

- Yapıldı (yalnız ön yüz): mevcut kullanıcı dönüşü `/` (ana uygulama) sayfasına gider, giriş formu yeniden gösterilmez; MFA dönüşü ikinci adım ekranını gösterir; hata kodları sınıflandırılır; Auth sahnesi (navbar/footer/video) dönüş sayfasında da aynıdır.
- Yapılmadı: Google kullanıcı adı ekranı (backend gerekir), Steam e-postasız kayıt (ürün kararı).
