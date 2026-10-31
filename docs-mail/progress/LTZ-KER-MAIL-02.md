# LTZ-KER-MAIL-02 — Hesap E-postaları ve TR/EN Şablonlar

Sorumlu: Kerem. Görevler: MAIL-01, 02, 03, 04, 05, 08, 09. Durum: **KOD + TEST TAMAM** (2026-10-31), `WELCOME` ve `SUSPICIOUS_LOGIN` hariç. Ön koşul: MAIL-01 fazı (kuyruk/SMTP) tamam.

## Gerçekleşen durum (2026-10-31)

- Şablon türleri (V82 seed, her biri TR + EN, `MailType`): `EMAIL_VERIFICATION`, `PASSWORD_RESET_LINK`, `ACCOUNT_DELETION_CONFIRMATION`, `PASSWORD_CHANGED`, `PASSWORD_RESET_COMPLETED`, `EMAIL_CHANGED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `ACCOUNT_DISABLED`, `ACCOUNT_REACTIVATED` (kodda: `SecurityNotificationType` → `MailType` eşlemesi). Aşağıdaki tablodaki ad farkları: kod `EMAIL_VERIFICATION` doğrulama + e-posta değişikliği kodunu birlikte karşılar; `PASSWORD_RESET` → `PASSWORD_RESET_LINK`, `ACCOUNT_DELETION` → `ACCOUNT_DELETION_CONFIRMATION`.
- **Yok:** `WELCOME` (MAIL-05, Auth tetikleyicisi yok), `SUSPICIOUS_LOGIN` (MAIL-03 kapsamında, Auth'ta tetikleyici yok). Çağıran çıkınca eklenir.
- Render: `layout.html` + `ltz-logo.png` (CID gömülü), HTML-escape, başlıkta CRLF temizliği, iki dilli (TR+EN) varsayılan, bağlantı host allowlist (`LinkPolicy`). `TemplateRendererTest` (6) + `MailDispatchServiceTest` içinde iki dilli/tek dilli gönderim.
- **Yapılmadı:** Mailpit ile elle uçtan uca deneme; logonun gözle kontrolü; Auth'a gönderilecek adaptör talebi (`docs-auth/verilen-cevaplar/mail-service/`).

## Kapsam

| Görev | Şablon kodu | İçerik değişkeni | Not |
| --- | --- | --- | --- |
| MAIL-01 | `EMAIL_VERIFICATION`, `EMAIL_CHANGE` | `code` (6 hane), `validitySeconds` | Kod Auth'ta üretilir; Mail yalnız iletir, düz kalıcı tutmaz |
| MAIL-02 | `PASSWORD_RESET`, `ACCOUNT_DELETION` | `link`, `validitySeconds` | Bağlantı frontend adresini Auth üretir; Mail host/allowlist doğrulaması yapar (açık yönlendirme/`javascript:` yok) |
| MAIL-03 | `PASSWORD_CHANGED`, `PASSWORD_RESET_COMPLETED`, `EMAIL_CHANGED` (eski adrese), `SUSPICIOUS_LOGIN` | `occurredAt` | İçerikte parola/token/bağlantı yok; şüpheli giriş tetikleyicisi Auth'ta henüz yok |
| MAIL-04 | `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `ACCOUNT_DISABLED`, `ACCOUNT_REACTIVATED` | `occurredAt` | Gerekçe metni taşınmaz (kişisel/UGC riski) — onay sorusu |
| MAIL-05 | `WELCOME` | — | Tetik: Auth, hesap ACTIVE olunca |
| MAIL-08 | tümü | — | Responsive HTML (tablo tabanlı, inline CSS, 600 px), düz metin alternatifi, LTZ logosu (dosya/URL repoda aranacak; uzak görsel yerine barındırılan HTTPS URL ya da CID — K karar), koyu mod uyumlu |
| MAIL-09 | tümü | `locale` | `tr`/`en`; eksik çeviri = başlangıçta başarısızlık (test); bilinmeyen locale → `tr` |

## İş kırılımı
1. V82 seed: `mail_templates` (code × locale × version, `active`) — SQL içinde HTML kaçışsız sabit metin; değişken yer tutucular allowlist.
2. `TemplateRenderer` (HTML-escape zorunlu, değişken şeması şablona bağlı, fazla/eksik değişken hata), `SubjectLine` (başlık enjeksiyonu: satır sonu temizliği).
3. Tür başına doğrulayıcı (`type` ↔ zorunlu değişkenler ↔ çağıran `sub`).
4. Auth adaptörü sözleşmesi netleşince Auth'a yazılacak talep (`docs-auth/verilen-cevaplar/mail-service/`), Mailpit ile uçtan uca elle doğrulama.
5. Testler: her tür × TR/EN render snapshot'ı, escape (XSS yük dizileri), başlık enjeksiyonu, eksik değişken, locale geri dönüşü, sır sızıntısı yok, link allowlist.
