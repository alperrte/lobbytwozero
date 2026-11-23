# KVKK / Aydınlatma metni: Review (oyun incelemeleri) maddesi

Son güncelleme: 12 Kasım 2026. Hazırlayan: Review servisi (Hamza). Madde metni ekip tarafından hazırlandı.

> **Durum:** Madde metni **hazır**; olgular koddaki davranışa karşı doğrulandı (§2). Hukuki sebep, saklama süreleri ve başvuru yolları **hukuk onayı** bekliyor (§6). Sitedeki `/legal/kvkk` sayfası bugün yalnız "bu metin henüz yayınlanmadı" iskeletidir (`shared/components/layout/legal-page.tsx`; projede "hukuki metin uydurulmaz" kuralı vardır). Aydınlatma metni servislerin maddelerinin birleştiği **tek bir belgedir**; yalnız Review maddesiyle yayınlanırsa sayfa eksik bir metni tam gibi gösterir. Bu yüzden madde **sayfaya eklenmedi**; tüm metin ve hukuk onayı tamamlandığında §7'deki adımlarla taşınır. Bu belge hukuki görüş değildir.

## 1. Neden gerekli?

12 Kasım 2026'da kabul edilen karar (bkz. [REVIEW_SERVICE_DECISIONS.md](REVIEW_SERVICE_DECISIONS.md) → "yazar kimliği `authorUserId` açılır") şu sonuçları doğurdu. Kullanıcı bunları bilmeden incelemesini yazmamalıdır:

1. İnceleme yazarının **kullanıcı kimliği (UUID)**, giriş yapmış her kullanıcıya iletilir. Aynı yazarın farklı oyunlardaki incelemeleri bu kimlik üzerinden birbirine bağlanabilir.
2. İncelemede yazarın **görünen adı, kullanıcı adı ve avatarı** gösterilir; bu, profil gizlilik ayarından **bağımsızdır** (User servisinin `REVIEW_AUTHOR` yorum/UGC kuralı). Profilini gizleyen kullanıcının adı da incelemesinde görünür.
3. Hesap yasaklı/devre dışı ise ya da kullanıcılar arasında engel varsa ad/avatar gösterilmez; bu durumlarda "Bir oyuncu" yazılır ve profil bağlantısı verilmez (nedeni ayrıştırılmaz).

## 2. Metne taşınan olgular (kodla doğrulandı)

| Konu | Olgu | Doğrulama |
| --- | --- | --- |
| İşlenen veri | İnceleme metni, puan (1-10), tavsiye, beğeni, yazar kimliği; yazar adı/kullanıcı adı/avatarı User servisinden gösterim anında alınır | `ReviewResponse(authorUserId, body, …)`; User `profiles/batch` |
| Kimlere gösterilir | Giriş yapmış kullanıcılara. Misafirler Review uçlarına erişemez | Oturumsuz okuma ve yazma `401` (canlı denendi, 12 Kasım 2026) |
| Amaç | Oyun incelemelerinin ve yazarlarının topluluğa gösterilmesi; kötüye kullanımın önlenmesi (raporlama, moderasyon, hız sınırı) | Moderasyon (V60), rapor (V58), kota |
| Gizlilik ayarı | Profil gizlilik ayarı incelemedeki ad/avatar gösterimini **kapatmaz** | User `REVIEW_AUTHOR` kuralı |
| Moderasyon | İncelemeler raporlanabilir; moderatör/yönetici gizleyebilir (`HIDDEN`) veya arşivleyebilir (`ARCHIVED`). Gizlenen inceleme başkalarına görünmez; yazar kendi incelemesini ve durumunu görür | Canlı: HIDE → yazar `HIDDEN` görür, herkese açık listede yok |
| Raporu veren | Raporu veren kullanıcının kimliği yazara veya başka kullanıcılara iletilmez; yönetim yanıtlarında da yer almaz | `ReviewReportResponse` ve `AdminReportResponse` alanlarında raportör kimliği yoktur |
| Başka servislere aktarım | **User**: görünür inceleme sayısı profil özeti olarak (kullanıcı kimliği ve sayı). **Game**: yalnız oyun başına toplu puan özeti (ortalama ve adet), kişi bazlı veri yok | `ReviewSummaryChanged` olayı; `rating-summaries` iç ucu |
| Silme | Hesap silinirse Review'daki incelemeler, puanlar, tavsiyeler, beğeniler ve kullanıcının verdiği raporlar silinir; silinen yazarın incelemelerine yapılan raporlar da kalkar. Bekleyen sayaç olayları silinir. Moderasyon denetim kaydında moderatör kimliği anonimleşir (kayıt ve gerekçe kalır). Hız sınırı sayaçları silinir. Yalnız bir **silme engeli kaydı** kalır: hesap kimliğinin SHA-256 özeti (hash) ve bitiş zamanı; süre 15 dk (erişim jetonu üst sınırı) + 2×30 sn saat toleransı + 1 dk = **yaklaşık 17 dakika** ve süresi dolan satırı temizlik işi siler | `AccountErasureService.erase`, `AccountWriteLock`, `UserTokenPolicy.ERASURE_BLOCK_TTL`, `ErasureGuardCleanup` |

## 3. Madde metni (Türkçe)

> **Oyun İncelemeleri, Puanlar ve Tavsiyeler**
>
> **Hangi verileri işliyoruz?** Oyunlara yazdığınız incelemeleri, verdiğiniz puanları (1-10), tavsiyelerinizi ve beğenilerinizi; bunlarla birlikte kullanıcı kimliğinizi işleriz. İncelemenizle gösterilen görünen adınız, kullanıcı adınız ve avatarınız profil hizmetimizden gösterim anında alınır.
>
> **Kimlere ve nasıl gösteriliyor?** İncelemeleriniz, puanlarınız ve tavsiyeleriniz platformda **giriş yapmış** diğer kullanıcılara gösterilir; misafirler bunlara erişemez. İncelemenizle birlikte kullanıcı kimliğiniz, görünen adınız, kullanıcı adınız ve avatarınız, **profil gizlilik ayarlarınızdan bağımsız olarak** gösterilir. Aynı kullanıcı kimliği üzerinden farklı oyunlardaki incelemeleriniz birbirine bağlanabilir. Başka bir kullanıcı sizi engellemişse veya hesabınız kısıtlanmışsa adınız ve avatarınız gösterilmez; bu durumda incelemeniz "Bir oyuncu" adıyla görünür ve nedeni belirtilmez.
>
> **Amaç.** Bu veriler, oyun incelemelerinin ve yazarlarının toplulukla paylaşılması ile platformun kötüye kullanımının önlenmesi (raporlama, moderasyon ve hız sınırlaması) amacıyla işlenir.
>
> **Raporlama ve moderasyon.** İncelemeler topluluk kurallarına uygunluk açısından diğer kullanıcılar tarafından raporlanabilir. Yetkili moderatörler bir incelemeyi gizleyebilir veya arşivleyebilir; gizlenen inceleme diğer kullanıcılara görünmez, siz ise incelemenizi ve durumunu görmeye devam edersiniz. Raporu veren kullanıcının kimliği size veya diğer kullanıcılara iletilmez.
>
> **Başka hizmetlerle paylaşım.** Yazdığınız görünür inceleme sayısı profil hizmetimize özet olarak iletilir. Oyun hizmetimize yalnız oyun başına toplu puan özeti (ortalama puan ve puan sayısı) iletilir; kişi bazlı veri iletilmez.
>
> **Silme.** Hesabınızı sildiğinizde incelemeleriniz, puanlarınız, tavsiyeleriniz, beğenileriniz ve yaptığınız raporlar silinir; silmenin ardından, silinen verilerin işlem sırasında yeniden yazılmasını engellemek için kimliğinizin geri döndürülemez özetini (hash) içeren teknik bir kayıt yaklaşık 17 dakika tutulur ve ardından otomatik silinir.
>
> *[Hukuki sebep, saklama süreleri ve başvuru yolları metnin genel bölümlerinde yer alır. Hukuk/ürün sahibi tamamlar; bkz. §6.]*

## 4. Madde metni (English)

> **Game Reviews, Ratings and Recommendations**
>
> **What data we process.** We process the reviews you write for games, the ratings (1-10) you give, your recommendations and likes, together with your user ID. The display name, username and avatar shown with your review are retrieved from our profile service at the time they are displayed.
>
> **Who sees it and how.** Your reviews, ratings and recommendations are shown to other **signed-in** users; guests cannot access them. Your user ID, display name, username and avatar are shown with your review **regardless of your profile privacy settings**, and your reviews for different games can be linked through the same user ID. If another user has blocked you or your account is restricted, your name and avatar are not shown; your review then appears as "A player" and the reason is not stated.
>
> **Purpose.** This data is processed to share game reviews and their authors with the community and to prevent misuse of the platform (reporting, moderation and rate limiting).
>
> **Reporting and moderation.** Reviews can be reported by other users for compliance with community rules. Authorized moderators can hide or archive a review; a hidden review is not visible to other users, while you can still see your review and its status. The identity of the user who reported a review is not disclosed to you or to other users.
>
> **Sharing with other services.** The number of your visible reviews is sent to our profile service as a summary. Our game service receives only an aggregate rating summary per game (average rating and number of ratings); no per-person data is sent.
>
> **Deletion.** When you delete your account, your reviews, ratings, recommendations, likes and the reports you made are deleted; afterwards, to prevent deleted data from being written again while the deletion is processed, a technical record containing an irreversible hash of your identifier is kept for about 17 minutes and then deleted automatically.
>
> *[Legal basis, retention periods and request channels belong to the general sections of the notice; to be completed by legal/product. See §6.]*

## 5. Gizlilik politikası (`/legal/privacy`) için tutarlı kısa paragraf

**Türkçe.** Oyun incelemeleri, puanlar ve tavsiyeler giriş yapmış kullanıcılara gösterilir. İncelemenizle birlikte kullanıcı kimliğiniz, görünen adınız, kullanıcı adınız ve avatarınız, profil gizlilik ayarlarınızdan bağımsız olarak görünür; bu bilgilerle farklı oyunlardaki incelemeleriniz birbirine bağlanabilir. Hesabınızı sildiğinizde bunlar silinir. Ayrıntı için KVKK Aydınlatma Metni'ndeki "Oyun İncelemeleri, Puanlar ve Tavsiyeler" bölümüne bakın.

**English.** Game reviews, ratings and recommendations are shown to signed-in users. Your user ID, display name, username and avatar are shown with your review regardless of your profile privacy settings, and your reviews for different games can be linked through them. They are deleted when you delete your account. See the "Game Reviews, Ratings and Recommendations" section of the KVKK Notice for details.

## 6. Hukuk/ürün sahibine açık sorular (metne yazılmadı, uydurulmadı)

| # | Konu | Not |
| --- | --- | --- |
| 1 | **Hukuki sebep** (ör. KVKK m.5/2 hangi bent) | Metinde yazılı değil; hukuk belirler |
| 2 | **Saklama süreleri**: silme engeli kaydı (kimlik özeti, yaklaşık 17 dk, otomatik silinir) kişisel veri sayılır mı; moderasyon denetim kaydı (moderatör kimliği anonim, gerekçe kalır) ve gizlenen/arşivlenen incelemelerin saklanması | İlk madde için süre kodda sabittir (§2); diğerlerinde süre tanımı yoktur, hukuk belirler |
| 3 | **Profil gizliliğinden bağımsız gösterim** kullanıcı için beklenmedik olabilir; açık rıza veya itiraz yolu gerekir mi? | Ürün/hukuk kararı. Teknik olarak değiştirmek User `REVIEW_AUTHOR` kuralını etkiler |
| 4 | **Başvuru yolları** (erişim, düzeltme, silme, itiraz) ve veri sorumlusu bilgileri | Metnin genel bölümleri |
| 5 | **Yurt dışına aktarım** ve **üçüncü taraf sağlayıcılar** | Review kendi başına yurt dışına veri aktarmaz, bu yalnız mimari olgudur; genel değerlendirme hukukta |
| 6 | **Çocukların verileri / yaş**: yaş doğrulaması Auth'tadır (beyana dayalı, doğum tarihi saklanmaz); Review yaş bilgisi işlemez | Auth'un kendi aydınlatma maddesi ayrıca yazılmalı |

## 7. Yayına alma adımları (hukuk onayından sonra)

1. Tüm servislerin maddeleri tek metinde birleştirilir (Review maddesi §3/§4 aynen alınır, köşeli parantezli yer tutucular hukuk metniyle değiştirilir).
2. `frontend/src/shared/components/layout/legal-page.tsx` bu sayfa için "iskelet" yerine içerik gösterecek şekilde genişletilir; metinler `Legal` ad alanına (`tr.json` ve `en.json`) taşınır, her iki dilde anahtar tamlığı `messages.test.ts` ile denetlenir. Sayfalar `noindex` kalır.
3. `/legal/privacy` için §5 paragrafı eklenir; iki sayfa aynı olguları söyler.
4. `docs/api.md` değişmez (arayüz davranışı), `REVIEW_SERVICE_PROGRESS.md`'ye tarihli not düşülür.
