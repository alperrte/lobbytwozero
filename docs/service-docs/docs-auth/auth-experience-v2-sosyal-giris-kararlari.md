# Auth Experience V2: Google ve Steam ilk giriş (kullanıcı adı)

Durum: **uygulandı** (2026-11-08). Bu belge ne yapıldığını ve hâlâ açık olan ürün kararlarını söyler.

## Yeni davranış

| Durum | Ne oluyor |
|---|---|
| Bağlı Google/Steam kullanıcısı | Parola girişiyle aynı kurallar (durum, 2FA, zorunlu rol). Oturum açılır, ön yüz `/` sayfasına gider. Kullanıcı adı sorulmaz. |
| İlk kez Google | Hesap AÇILMAZ. Doğrulanmış `sub` ve Google'ın doğruladığı e-posta sunucuda `google_registrations` tablosunda bekler; ön yüze tek kullanımlık, 15 dk geçerli kayıt token'ı gider (`#status=registration_required&provider=google&token=…`). Kullanıcı **yalnız kullanıcı adı** seçer; `POST /oauth/google/complete` hesabı (ACTIVE, doğrulanmış e-posta, PLAYER) ve `sub` bağını tek işlemde açar, ardından oturum/2FA akışı çalışır. |
| İlk kez Steam | Aynı biçim (`steam_registrations`, `provider=steam`). Hesap **e-postasız** ve ACTIVE açılır; e-posta sorulmaz, doğrulama beklenmez. Zorunlu 2FA ayarı açıksa (üretim) kurulum challenge'ı döner, MFA atlanmaz. |
| Google e-postası zaten kayıtlı | Sessiz birleştirme yok: `ACCOUNT_LINK_REQUIRED` (callback'te ve tamamlamada). |
| Eski `g_xxx` adlı Google hesapları, e-postayla açılmış bekleyen Steam hesapları | Dokunulmadı; çalışmaya devam eder. |

Güvenlik: kayıt token'ı yalnız özet olarak saklanır, tek kullanımlıktır, `FOR UPDATE` ile yarışa karşı kilitlenir; kullanıcı adı çakışmasında token tüketilmez; istemci e-posta ya da sağlayıcı kimliği gönderemez (bilinmeyen alan 400); tamamlama uçları CSRF korumalıdır (oturum cookie'si yazdığı için). Veritabanı: `V70` (`accounts.email` boş olabilir + tutarlılık kısıtları, `google_registrations`).

## E-postasız Steam hesabında neler çalışmaz (açıkça)

- **Hesap silme onayı** e-postayla gider: `409 ACCOUNT_EMAIL_REQUIRED`.
- **Parola belirleme kodu** e-postayla gider: `409 ACCOUNT_EMAIL_REQUIRED`.
- **Parola sıfırlama**: adres olmadığı için hesap bulunamaz (yanıt hesap varlığını sızdırmaz).
- **Güvenlik bildirimleri** gönderilmez (alıcı yok, sahte adres üretilmez).
- **Yeniden giriş**: Steam ile. Steam erişimi kaybedilirse e-posta kurtarma yolu yoktur.
- **Sonradan e-posta ekleme**: çalışır. `POST /email/change` parolasız hesapta TOTP/kurtarma koduyla yeniden doğrular; yeni adres koduyla doğrulanır. TOTP olmayan e-postasız hesapta yeniden doğrulama yapılamaz (üretimde Steam hesapları için TOTP zorunludur).

## Hâlâ ürün/güvenlik kararı isteyenler

1. **Hesap kurtarma:** E-postasız Steam hesabı için kurtarma kodlarının zorunlu tutulması ve "e-posta ekle" çağrısının arayüzde gösterilmesi (ayarlar sayfası henüz yok).
2. **Hesap silme:** E-postasız hesap kendini silemez. Alternatif onay kanalı (ör. TOTP ile onay) istenirse ayrı iş.
3. **Mevcut e-posta hesabını sağlayıcıya bağlama** (ACCOUNT_LINK_REQUIRED sonrası) bağlama uçlarıyla ayrıca çalışır; değişmedi.
