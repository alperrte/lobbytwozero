# KVKK / Aydınlatma metni için Review madde önerisi

Son güncelleme: 12 Kasım 2026. Hazırlayan: Review servisi (Hamza).

> **Durum:** Bu belge bir **taslak önerisidir**; hukuki metin değildir. Sitedeki `/yasal/kvkk` sayfası henüz yayınlanmış bir metin içermez (iskelet sayfa: "henüz yayınlanmadı" bilgisi gösterir; projede "hukuki metin uydurulmaz" kuralı vardır). Bu yüzden bu içerik sayfaya **eklenmedi**. Aydınlatma metnini yayınlayacak kişi/ekip aşağıdaki **olguları** metne taşır; hukuki dil, hukuki sebep ve saklama süreleri hukuk/ürün sahibinin işidir.

## 1. Neden gerekli?

Review servisinde 12 Kasım 2026'da kabul edilen karar (bkz. [REVIEW_SERVICE_DECISIONS.md](REVIEW_SERVICE_DECISIONS.md) → "yazar kimliği `authorUserId` açılır") şu sonuçları doğurdu. Kullanıcı bunları bilmeden incelemesini yazmamalıdır:

1. İnceleme yazarının **kullanıcı kimliği (UUID)**, giriş yapmış her kullanıcıya iletilir. Aynı yazarın farklı oyunlardaki incelemeleri bu kimlik üzerinden birbirine bağlanabilir.
2. İncelemede yazarın **görünen adı, kullanıcı adı ve avatarı** gösterilir; bu, profil gizlilik ayarından **bağımsızdır** (User servisinin `REVIEW_AUTHOR` yorum/UGC kuralı). Profilini gizleyen kullanıcının adı da incelemesinde görünür.
3. Hesap, yasaklı/devre dışı ise ya da kullanıcılar arasında engel varsa ad/avatar gösterilmez; bu durumlarda "Bir oyuncu" yazılır ve profil bağlantısı verilmez (nedeni ayrıştırılmaz).

## 2. Metne taşınacak olgular (doğrulanmış davranış)

| Konu | Olgu |
| --- | --- |
| İşlenen veri | İnceleme metni, puan, tavsiye, beğeni, yazar kimliği; yazar adı/avatarı User servisinden gösterim anında alınır |
| Kimlere gösterilir | Giriş yapmış kullanıcılara. Misafirler Review uçlarına erişemez |
| Amaç | Oyun incelemelerinin ve yazarlarının topluluğa gösterilmesi, kötüye kullanımın önlenmesi (raporlama/moderasyon) |
| Gizlilik ayarı | Profil gizlilik ayarı incelemedeki ad/avatar gösterimini **kapatmaz** |
| Moderasyon | İncelemeler raporlanabilir; moderatör/yönetici incelemeyi gizleyebilir veya arşivleyebilir. Gizlenen inceleme başkalarına görünmez, yazarı kendi incelemesini ve durumunu görür |
| Rapor veren | Raporu veren kullanıcının kimliği yazara veya başka kullanıcılara iletilmez |
| Profil sayacı | Görünür inceleme sayısı User profiline özet olarak iletilir |
| Silme | Hesap silinirse Review'daki incelemeler, puanlar, tavsiyeler, beğeniler ve verilen raporlar silinir. Moderasyon denetim kayıtlarında moderatör kimliği anonimleştirilir |

## 3. Önerilen madde taslağı (Türkçe)

> **Oyun incelemeleri.** Oyunlara yazdığınız incelemeler, puanlar ve tavsiyeler platformda giriş yapmış diğer kullanıcılara gösterilir. İncelemenizle birlikte kullanıcı kimliğiniz, görünen adınız, kullanıcı adınız ve avatarınız, profil gizlilik ayarlarınızdan bağımsız olarak gösterilir; aynı kullanıcı kimliği üzerinden farklı oyunlardaki incelemeleriniz birbirine bağlanabilir. Başka bir kullanıcı tarafından engellenmişseniz veya hesabınız kısıtlanmışsa adınız ve avatarınız gösterilmez. İncelemeler, topluluk kurallarına uygunluk açısından kullanıcılar tarafından raporlanabilir ve yetkili moderatörler tarafından gizlenebilir veya arşivlenebilir; raporu veren kullanıcının kimliği size iletilmez. Hesabınızı sildiğinizde incelemeleriniz, puanlarınız, tavsiyeleriniz ve beğenileriniz silinir. *[Hukuki sebep, saklama süreleri ve başvuru yolları metnin genel bölümlerinde yer alır — hukuk/ürün sahibi tamamlar.]*

## 4. Önerilen madde taslağı (English)

> **Game reviews.** The reviews, ratings and recommendations you write are shown to other signed-in users. Your user ID, display name, username and avatar are shown with your review regardless of your profile privacy settings, and your reviews for different games can be linked through the same user ID. If another user has blocked you or your account is restricted, your name and avatar are not shown. Reviews can be reported by users and hidden or archived by authorized moderators; the identity of the reporter is not disclosed to you. When you delete your account, your reviews, ratings, recommendations and likes are deleted. *[Legal basis, retention periods and request channels belong to the general sections of the notice — to be completed by legal/product.]*

## 5. Açık

- Metni yayınlayacak sahibin belirlenmesi ve maddenin metne eklenmesi.
- Gizlilik politikasında ("`/yasal/gizlilik`") aynı olguların tutarlı yazılması.
