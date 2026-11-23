# Auth — Anahtar Rotasyonu (S9)

Durum: IMPLEMENTED (2026-11-05). Kapsam: TOTP gizli anahtarlarının AES anahtarı ve doğrulama kodlarının/kurtarma kodlarının HMAC anahtarı. JWT imza anahtarı ayrıdır (`AUTH_JWT_ADDITIONAL_PUBLIC_KEYS`, `.env.example` rotasyon notu).

## Neden

Anahtar sızarsa ya da düzenli yenileme politikası uygulanacaksa, anahtarı değiştirirken kullanıcıların TOTP'si ve kullanılmamış kurtarma kodları bozulmamalıdır. Bu yüzden eski anahtar bir süre yalnız **doğrulamak/çözmek** için tutulur, yeni değerler yeni anahtarla üretilir.

## Ayarlar (hepsi isteğe bağlı; yoksa davranış öncekiyle aynıdır)

| ENV | Anlamı |
| --- | --- |
| `AUTH_TOTP_ENCRYPTION_KEY` | Güncel TOTP şifreleme anahtarı (Base64, 32 bayt) |
| `AUTH_TOTP_ENCRYPTION_KEY_VERSION` | Güncel anahtarın sürümü (varsayılan `1`); satırdaki `totp_credentials.key_version` ile eşleşir |
| `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS` | Eski sürümler: `1=<base64>,2=<base64>`. Yalnız çözmede kullanılır; güncel sürümle çakışan veya yinelenen sürüm açılışı durdurur |
| `AUTH_OTP_HMAC_KEY` | Güncel HMAC anahtarı (Base64, en az 32 bayt) |
| `AUTH_OTP_HMAC_PREVIOUS_KEY` | Önceki HMAC anahtarı; kayıtlı özetler bununla da karşılaştırılır |

## Nasıl çalışır

- **TOTP:** yeni şifrelemeler güncel sürümle yapılır; çözerken satırın `key_version` değerine göre anahtar seçilir. Eski sürümdeki satır, kullanıcı TOTP ile **her başarılı doğrulamada** güncel anahtarla yeniden şifrelenir. Bilinmeyen sürüm güvenli biçimde hata verir.
- **HMAC:** yeni özetler her zaman güncel anahtarla üretilir. Kayıtlı özetle karşılaştıran yerler (kurtarma kodu, e-posta doğrulama/değiştirme/parola belirleme kodu) önce güncel, sonra önceki anahtarın özetini dener. Kurtarma kodu tek kullanımlık olduğundan eşleşince tüketilir, yeniden özetlenmez. Deneme sayaçları ve PKCE/nonce kısa ömürlüdür; rotasyonda kendiliğinden yenilenir (devam eden bir giriş akışı en çok akış süresi kadar etkilenir).

## Adımlar (geliştirme)

```text
node ops/generate-auth-dev-keys.mjs --rotate-totp   # güncel anahtarı PREVIOUS_KEYS'e taşır, sürümü artırır, yenisini üretir
node ops/generate-auth-dev-keys.mjs --rotate-hmac   # güncel anahtarı PREVIOUS_KEY'e taşır, yenisini üretir
# Auth Service'i yeniden başlatın.
```

Betik anahtar değerlerini ekrana yazmaz, tüm hedef dosyalara (`.env`, `.env.dev`) aynı yeni değerleri yazar ve satır sonlarını korur. `--rotate-hmac`, önceki anahtar zaten doluysa üzerine yazmayı reddeder (kullanılmamış kurtarma kodları geçersiz kalır); eski kodlar yenilendikten sonra `--force` ile tekrarlanır.

## Üretim

Anahtarlar sır yöneticisinde tutulur; aynı sırayı elle uygulayın: yeni anahtarı üretin, eskisini `PREVIOUS` alanına ekleyin, sürümü artırın, servisi yeniden başlatın.

## Eski anahtar ne zaman kaldırılır

- **TOTP:** `select key_version, count(*) from totp_credentials group by key_version` yalnız güncel sürümü gösterdiğinde eski sürüm `PREVIOUS_KEYS`'ten silinebilir. Hiç giriş yapmayan kullanıcıların satırları eski sürümde kalır; silmeden önce bu sayıyı kontrol edin.
- **HMAC:** kurtarma kodları kullanılana ya da kullanıcı TOTP'yi kapatıp yeniden kurarak (kodları yenileyerek) değiştirene kadar önceki anahtarın kalması gerekir. Kaldırırsanız kullanılmamış eski kurtarma kodları çalışmaz; kullanıcılar kodlarını yenilemelidir.
