# USER-SVC-REQ-FE-01 — Frontend'in user-service ile çalışma sözleşmesi

Kime: Alper (frontend iskeleti, Gateway) ve `features/user` üzerinde çalışacak herkes. Gönderen: Kerem / user-service. Tarih: 2026-11-06. Durum: OPEN.
Bu belge kod yazmaz. Frontend'de `features/user` başlamadan önce **doğrulanmış gerçekleri**, **önerilen yapıyı** ve **Alper'den beklenen kararları** tek yerde toplar. Her madde "Doğrulandı" ya da "Öneri" diye işaretlidir; ikisi karıştırılmamalıdır.

## 1. Durum

- user-service backend'i hazır; uçların tamamı `docs/api.md` → `## USER` altında `IMPLEMENTED` (Steam bağlama tarafı Auth'a bağlı, bkz. §6).
- Frontend'de `src/features/user/{components,hooks,schemas,services,types}` boş (yalnız `.gitkeep`). `useShellUser()` hep `null` döner; gerçek oturum bağlanınca yalnız bu hook değişecek.
- Kaynak önceliği: repo kodu > `docs/api.md` > bu belge. Çelişki görürsen api.md kazanır; bana haber ver.

## 2. Uçlar (Doğrulandı: `docs/api.md` USER bölümü)

Hepsi Gateway üzerinden, önek kırpılmadan: `{API_BASE}/api/v1/user/...`. Ayrıntılı gövde ve hata kodları için api.md.

| Ne | Uç | Kimlik | Sayfa/özellik |
| --- | --- | --- | --- |
| Kendi profilim | `GET /me` | Üye | Profilim |
| Profil güncelle | `PATCH /me/profile` | Üye, `version` | Profil düzenle |
| Avatar / kapak yükle | `PUT /me/avatar`, `PUT /me/cover` | Üye | Profil düzenle |
| Avatar / kapak kaldır | `DELETE /me/avatar`, `DELETE /me/cover` | Üye | Profil düzenle |
| Görsel oku | `GET /media/{fileName}` | Herkese açık | `<img src>` |
| Gizlilik | `GET` / `PUT /me/privacy` | Üye, `version` | Ayarlar |
| Açık profil | `GET /profiles/{userId}`, `GET /profiles/by-username/{username}` | **Misafir de olur**, token varsa doğrulanır | Oyuncu profili |
| Toplu kart | `POST /profiles/batch` | Üye | Arkadaş/mesaj listelerinde ad+avatar |
| Oyuncu arama | `GET /search?q=` | Üye (misafir `401`) | Arama |
| Tercihler | `GET` / `PUT /me/preferences` | Üye, `version` | Ayarlar |
| Onboarding | `GET /me/onboarding`, `POST /me/onboarding/complete` | Üye, `version` | İlk kurulum |
| Bağlı hesaplar | `GET /me/linked-accounts`, `PATCH /me/linked-accounts/{provider}` | Üye, `version` | Ayarlar (Steam) |

`/internal/**` uçları frontend içindir değil; Gateway'de rotası da yok.

## 3. Frontend'i doğrudan ilgilendiren sözleşme noktaları (Doğrulandı)

1. **Görsel yükleme multipart değil.** Gövde ham görseldir; `Content-Type` `image/jpeg|png|gif` olmalı, başka tür `415`. Sınırlar: avatar 2 MB, kapak 4 MB. `FormData` ile gönderirsek çalışmaz.
2. **Görsel adresleri göreli** (`/api/v1/user/media/...`) ve değişmezdir (her yükleme yeni adres). `<img src>` için `API_BASE` ile birleştirilir. `avatar.animated=true` ise GIF'tir; `small` ve `large` aynı dosyadır.
3. **İyimser kilit:** `PATCH /me/profile`, `PUT /me/privacy`, `PUT /me/preferences`, onboarding ve linked-accounts gövdede `version` ister. Güncel olmayan sürüm `409 CONCURRENT_MODIFICATION`. Ekran bunu "başka yerde değişti, yenile" akışına bağlar; `version` hiç kaydedilmemişse `0`.
4. **PUT'lar kısmi değildir:** gizlilik ve tercihlerde tüm alanlar zorunlu, tanımsız alan `400`.
5. **`Cache-Control: no-store`** özel uçlarda var; istemci bunları önbelleğe almaz. Çıkışta ve hesap değişiminde user verisi temizlenir.
6. **Biyografi düz metindir.** HTML'e çevrilmeden saklanır; yalnız metin olarak render edilir (`dangerouslySetInnerHTML` yok).
7. **Açık profil:**
   - Geçersiz/süresi dolmuş token **misafir sayılmaz, `401`** olur (oturum yenileme akışı devreye girmeli).
   - `404 PROFILE_NOT_FOUND` birçok nedeni tek yanıtla saklar (yok, yasaklı, silinmekte, **engelli**). UI nedeni ayırt etmeye çalışmaz.
   - `summary.*.status`: `AVAILABLE | HIDDEN | UNAVAILABLE`. Sayı yalnız `AVAILABLE` iken doludur; `UNAVAILABLE` iken `0` yazılmaz, "henüz yok" gösterilir.
   - `visible` bayrakları, diğer servislere (Social/Library/Hardware/Performance) hangi bölüm için gidileceğini söyler; o servisler kararı yine kendi tarafında uygular.
8. **Toplu kart** en çok 50 kimlik alır; olmayan, yasaklı ve engelli hesaplar **sessizce atlanır** (yanıt, istenenden kısa olabilir). Liste ekranlarında kart başına istek atılmaz (web-rules §7.1 ile aynı ilke).
9. **Arama:** `q` 2–32 karakter, en çok 20 sonuç, sayfalama yok, misafire kapalı. Debounce ve "sorguyu daralt" mesajı UI'da.
10. **Hata gövdesi `ProblemDetail`; makine kodu `code` alanında** (`GlobalExceptionHandler`'da doğrulandı). Hata kodları her ucun "Hata Durumları" bölümünde listeli (ör. `INVALID_DISPLAY_NAME`, `INVALID_IMAGE`, `PAYLOAD_TOO_LARGE`, `ONBOARDING_INCOMPLETE`).

## 4. Önerilen yapı (Öneri — kararı Alper ile veriyoruz)

Alper'in iskeleti ve `docs/web-rules.md` ile uyumlu; yeni mimari kural uydurmuyoruz.

**Akış:** `component → hook → features/user/services → shared/lib/api/client.ts (apiRequest)`. Bileşende doğrudan `fetch` yok.

**Route yerleşimi** (Türkçe slug'lar `routing.ts` tablosuna eklenir):

| Sayfa | Grup | Not |
| --- | --- | --- |
| Açık profil `profiles/[username]` | `(public)` | Misafire açık, varsayılan `noindex` (web-rules §2) |
| Profilim / düzenle | `(dashboard)` | `noindex`, sitemap dışı |
| Ayarlar → gizlilik, tercihler, bağlı hesaplar | `(dashboard)` | Bugün `settings` `(public)` altında; hesap verisi içerdiğinden taşınması gerekir (§5, madde 4) |
| Onboarding | `(dashboard)` | İlk girişte yönlendirme |
| Oyuncu arama | `(dashboard)` | Misafire kapalı |

**Dosyalar:** `types/` api.md ile birebir (elle uydurulmaz); `schemas/` form doğrulama (sunucu nihai kaynak); `services/` yalnız `apiRequest` çağıran ince fonksiyonlar; `hooks/` durum ve hata yönetimi. Metinler `User.*` namespace'inde TR+EN.

**Sınır:** `features/user` Kerem'in; `shared/` ve `app/` kabuğu Alper'in. Ortak yere dokunma gerekirse önce o kişiye haber. Başka feature, `user`'ın iç dosyasını import etmez; navbar `useShellUser()` üzerinden okur.

**Test:** Kodun yanında Vitest (`*.test.ts`). Test yazımı fazlar bitince toplu yapılır; klasör ve adlandırma baştan hazır olur.

## 5. Alper'den beklenen karar/yanıtlar

1. **`apiRequest` genişletmesi (`shared/lib/api/client.ts`, Alper'in alanı).** Şu an yalnız `credentials` ekler, `ApiHttpError` yalnız `status` taşır. User için gerekenler (Auth'un frontend notlarıyla da örtüşüyor, `docs-auth/entegrasyon/gateway-ve-frontend.md`):
   - hata gövdesinden `ProblemDetail.code` ve alan hatalarının taşınması (web-rules §5 bunu zaten şart koşuyor);
   - durum değiştiren isteklerde CSRF: önce `GET /api/v1/auth/csrf` (`204`, çerezi üretir), sonra `X-XSRF-TOKEN` başlığı; değer `__Host-XSRF-TOKEN` çerezinden okunur;
   - `401` alınca `POST /api/v1/auth/session/refresh` ve isteği **bir kez** tekrar (access token kısa ömürlü). Eşzamanlı yenilemede `409 REFRESH_CONFLICT` gelir: tek yenileme yürütülür, istekler onu bekleyip tekrarlanır;
   - **oturum yoklaması `GET /api/v1/auth/me`** (Auth, PR #71, `docs/api.md` AUTH → Oturum): `200 { accountId, roles, authMethods, accessTokenExpiresAt }`, GET olduğu için CSRF istemez, `no-store`. `401` ise önce refresh, o da `401` ise misafir. `useShellUser()` kimlik ve rolü buradan alır; ad ve avatar için `GET /user/me` ayrıca çağrılır (`/auth/me` e-posta, kullanıcı adı, hesap durumu dönmez). `roles` yalnız arayüz içindir (menü), yetki sunucuda doğrulanır;
   - ham gövdeli yükleme (görsel) ve `Content-Type` yönetimi;
   - istek iptali (`AbortController`).
   Bunlar Auth/`features/auth` ile **ortak** ihtiyaç; tek yerde, tek sahiple yapılmalı. Soru: bunu sen mi yaparsın, ben mi yazıp PR ile göstereyim? Geriye uyum korunur (`ApiHttpError(status)` bozulmaz).
2. **Tercihlerin doğruluk kaynağı.** Bugün tema `localStorage` (`ltz-theme`), dil URL öneki + cookie, kenar çubuğu/navbar yine yerel. user-service ise giriş yapmış kullanıcı için `locale`, `theme`, `reducedMotion`, `compactLayout` tutar. Önerim: **yerel değer ilk boyamada kazanır** (FOUC olmaz); girişli kullanıcıda yerel→sunucu eşitlemesi arka planda yapılır, girişte sunucu değeri yerele yazılır. Seninle çakışmasın diye tema animasyonu işin bitip main'e girmeden bu kısma dokunmuyorum. Onayın?
3. **`web-rules` §14 ve §10 eski.** "Dil cookie tabanlı, ayrı `/tr` `/en` URL'si yok, `hreflang` uydurulmaz" diyor; URL önekli rotaya geçildi. Belge güncellenmeli (senin işin; ben dokunmuyorum).
4. **`settings` sayfasının grubu.** Şu an `(public)` içinde. Gizlilik, tercih ve bağlı hesaplar kullanıcıya özel olduğu için `(dashboard)` altına (noindex) alınmasını öneriyorum. Sayfanın bugünkü içeriği (Alper'in genel ayarları) ayrı kalabilir. Nasıl istersin?
5. **`features/auth` sahibi.** Görev tablosuna göre auth, user ve mail Kerem'in; `features/auth` ve `features/user` aynı kişide, sıra: önce `apiRequest` genişletmesi ve oturum, sonra user ekranları. Alper'e soru değil bilgi: kendi alanına (`shared/`) dokunan tek parça `apiRequest` değişikliği olduğundan §5.1 onay ister.

## 6. Henüz doğrulanmayanlar (açıkça)

- **Doğrulandı (2026-11-06, canlı):** gerçek Auth + User + Gateway, atılabilir Postgres/Mailpit ile HTTP üzerinden 30/30 kontrol geçti: `/auth/me` (misafir 401, girişli 200 ve yalnız 4 alan, `no-store`, bozuk token 401), refresh, çıkış sonrası 401; `/user/me` `userId == accountId`; `UserRegistered` ile `username` doluyor; CSRF'siz `PATCH` 403, CSRF'li 200, eski `version` 409 `CONCURRENT_MODIFICATION`, doğrulama hatasında gövdede `code` (`INVALID_DISPLAY_NAME`); avatar ham `image/png` 200, multipart 415, medya misafire açık; açık profil misafirde `details=null`, bozuk token 401, üyede dolu; arama misafire 401. Betik geçici (repoda yok). **Hâlâ denenmedi:** tarayıcıdaki gerçek frontend sayfaları (henüz yok), Steam OpenID adımı (Auth tarafında Kerem'in 2026-11-05 elle denemesi var), Google, e-posta dışı akışlar.
- **Steam bağlama:** Auth'ta `POST /api/v1/auth/oauth/steam/link/start` (yeniden doğrulama, `redirectUrl` döner) ve `.../link/callback` var (`docs/api.md` AUTH). **Kaldırma ucu bilerek yok** (2026-11-05 kararı: kullanıcı kaldıramaz, yalnız yönetici). Bu yüzden ayarlarda "Kaldır" düğmesi yapılmaz. Aynı kimlik başka hesapta bağlıysa Auth `EXTERNAL_IDENTITY_TAKEN` döner (ekranda ayrı mesaj). Auth bu kararı doğruladı ve `auth-requests-05` dosyasını "kapandı, kapsam daraltıldı" olarak hizaladı. **Doğrulandı (canlı, 2026-11-06):** Auth outbox'taki `EXTERNAL_IDENTITY_LINKED` satırını gerçek relay `user` hedefine teslim etti (`DELIVERED`), `GET /me/linked-accounts` `linked=true` ve `showOnProfile=false` döndü, `PATCH showOnProfile=true` sonrası üye görünümünde `details.steam` doldu. **Sınır:** outbox satırı ve `external_identities` kaydı SQL ile elle eklendi (gerçek Steam OpenID adımı atlandı); callback sonrası frontend'e dönüş ekran yazılmadan önce ayrıca doğrulanır.
- Arkadaş sayısı, Social başlangıç aktarımı bitene kadar `UNAVAILABLE` döner ([social-requests-01](../services/social-service-talepleri/social-requests-01.md)). Library/Review sayıları da Hamza'nın olaylarına bağlı. UI bunu "yok" diye değil "henüz yok" diye gösterir.
- Onboarding için favori tür/platform **katalog kodlarının kaynağı** (hangi liste, hangi servis) api.md'de net değil; ekranı yazmadan önce bana sor, uydurma.

## Kabul

- Alper §5'teki 1–4. maddelere yanıt verir (evet/hayır/değiştirelim); 5. madde bilgidir.
- Yanıttan sonra bu belge güncellenir ve `features/user` işi başlar; ondan önce frontend koduna dokunulmaz.
