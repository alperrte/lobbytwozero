# AUTH BELGELERİ — NEREDE NE VAR

Bu klasör Auth servisinin (auth-service ve Gateway güvenlik katmanı) bütün belgelerini toplar. Aradığın şeyi aşağıdaki "KİM NEYİ OKUMALI" bölümünden bul.

**Sahip:** Kerem · **Son güncelleme:** 2026-10-26

---

## KİM NEYİ OKUMALI

| Ben kimim | İlk okuyacağın | Sonra |
|---|---|---|
| **Servis sahibiyim, Auth'a bağlanacağım** | [`../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`](../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md) (token doğrulama, roller, silme ucu) | [`servis-sozlesmeleri.md`](servis-sozlesmeleri.md), [`d4-ekip-notlari/`](d4-ekip-notlari/) içinde kendi adına olan not |
| **Auth'tan bir şey istedim ya da sordum** | [`verilen-cevaplar/`](verilen-cevaplar/) içinde kendi branch'inin klasörü | O klasördeki talep dosyaları |
| **Frontend geliştiriciyim** | [`../docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`](../docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md) (cookie, CSRF, giriş akışları) | [`../docs/api.md`](../docs/api.md) içinde `## AUTH` |
| **Güvenlik bakıyorum** | [`acik-guvenlik-borclari.md`](acik-guvenlik-borclari.md) | [`sec-01-kapanis-raporu.md`](sec-01-kapanis-raporu.md), güvenlik taraması bölümü aşağıda |
| **Repo sahibiyim (Alper)** | [`ALPER_REPO_SAHIBI_NOTLARI.md`](ALPER_REPO_SAHIBI_NOTLARI.md) | [`verilen-cevaplar/hardware-service/`](verilen-cevaplar/hardware-service/README.md) |
| **Auth'u geliştiriyorum (Kerem)** | [`surec-borclari-plani.md`](surec-borclari-plani.md) | [`commit-plani-2026-10-24.md`](commit-plani-2026-10-24.md), [`d4-ekip-notlari/06_KEREM_TAKIP_LISTESI.md`](d4-ekip-notlari/06_KEREM_TAKIP_LISTESI.md) |
| **Auth'un nasıl çalıştığını öğrenmek istiyorum** | [`AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md`](AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md) | Faz raporları |

---

## HER DOSYA NE İŞE YARIYOR

### Sözleşmeler ve kurallar (servis sahipleri için)
| Dosya | Ne |
|---|---|
| [`servis-sozlesmeleri.md`](servis-sozlesmeleri.md) | Auth ile servisler arasındaki sözleşmeler: JWT, silme, servis token'ı, servis → Auth çağrıları (bölüm 3.4) |
| [`../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`](../docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md) | Servis sahibinin yapacakları adım adım |
| [`../docs/api.md`](../docs/api.md) | Tüm uçların kaydı, `## AUTH` bölümü |

### Cevaplar ve ekip notları
| Klasör / dosya | Ne |
|---|---|
| [`verilen-cevaplar/`](verilen-cevaplar/README.md) | Her branch'in Auth'tan istediği şeylere cevaplar, branch adıyla klasörlenmiş |
| [`d4-ekip-notlari/`](d4-ekip-notlari/) | Hesap silme için kişiye özel notlar (Umut, Hamza, Nisa, Alper) |
| [`ekip-uyum-denetimi-2026-10-24.md`](ekip-uyum-denetimi-2026-10-24.md) | Servislerin Auth sözleşmesine uyum denetimi |
| [`ALPER_REPO_SAHIBI_NOTLARI.md`](ALPER_REPO_SAHIBI_NOTLARI.md) | Repo sahibi olarak Alper'in kararları |

### Durum ve borçlar
| Dosya | Ne |
|---|---|
| [`acik-guvenlik-borclari.md`](acik-guvenlik-borclari.md) | Kapanmamış güvenlik işlerinin kaydı, kimin ne bekleyeceği |
| [`surec-borclari-plani.md`](surec-borclari-plani.md) | Doğrulama ve teslim sırası |
| [`commit-plani-2026-10-24.md`](commit-plani-2026-10-24.md) | Bakım turunun commit planı |

### Faz raporları (geçmiş kayıt)
| Dosya | Ne |
|---|---|
| [`faz-1-kapanis-raporu.md`](faz-1-kapanis-raporu.md) | Kayıt ve e-posta doğrulama |
| [`faz-2-kapanis-raporu.md`](faz-2-kapanis-raporu.md) | Giriş, JWT, oturum, 2FA |
| [`faz-3-kapanis-raporu.md`](faz-3-kapanis-raporu.md) | Steam ve Google |
| [`faz-4-kapanis-raporu.md`](faz-4-kapanis-raporu.md) | Parola değiştirme ve sıfırlama |
| [`faz-5-kapanis-raporu.md`](faz-5-kapanis-raporu.md) | Rol, yetki, hesap durumları |
| [`faz-6-kapanis-raporu.md`](faz-6-kapanis-raporu.md) | Hesap silme ve servis koordinasyonu |
| [`sec-01-kapanis-raporu.md`](sec-01-kapanis-raporu.md) | Güvenlik sertleştirmesi |

### Teknik referans
| Dosya | Ne |
|---|---|
| [`AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md`](AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md) | Auth'un tam teknik anlatımı (21 Ekim tarihli, bazı değerler güncellenmiştir, güncel değerler için borç kaydına bak) |

> **Neden dosyaları taşımadık:** Başka branch'lerin belgeleri ve kodlardaki yorumlar bu dosyalara `docs-auth/...` yoluyla bağlanıyor. Taşırsak o bağlantılar kırılır. Bu yüzden yerinde bıraktık ve bu sayfayı dizin olarak ekledik.

---

## ŞU AN GEÇERLİ ÖNEMLİ DEĞERLER (26 EKİM 2026)

| Konu | Değer |
|---|---|
| Access token ömrü | 60 saniye (`JWT_ACCESS_TTL_SECONDS`) |
| Banlı ya da çıkış yapan kullanıcının görünme süresi | En fazla 90 saniye (60 + 30 saniye saat toleransı) |
| Parola | En az 15 karakter, yaygın parola listesi ve desen kontrolü |
| Hesap silme | 14 gün geri alma süresi, sonra servislere silme görevi |
| Güvenlik olayı saklama | 365 gün |
| Silme ucu yazılmış servisler | 5 / 16 (Social, Game, Library, Review, Hardware) |
| Auth migration aralığı | V61–V70 (V66'ya kadar kullanıldı) |

---

## BAŞKALARININ BAĞLI OLDUĞU AUTH İMZALARI (DOKUNMA)

Game, Hardware, Library ve Review servislerinin testleri (`ProductionAuthSupport` ve `ProductionServiceTokenSupport`) Auth kaynak dosyalarını **doğrudan derleyip yansımayla çağırıyor**. Bu yüzden aşağıdaki imzalar bir sözleşme sayılır. Birini değiştirmek o servislerin testlerini sessizce kırar.

| Sınıf | Bağlı olunan imza |
|---|---|
| `JwtProperties` | `(String, String, Duration, String)` dört parametreli yapıcı |
| `JwtSigningKey` | `fromBase64Pkcs8(String)` ve `jwk()` |
| `JwksController` | `(JwtSigningKey)` yapıcısı ve `jwks()` |
| `AccessTokenIssuer` | `(JwtEncoder, JwtSigningKey, JwtProperties)` yapıcısı, `issue(Account, AuthSession, Instant)`, sonuçta `value()` |
| `ServiceTokenIssuer` | `(JwtEncoder, JwtSigningKey, JwtProperties, Clock)` yapıcısı, `issue(String, String, UUID)` |
| `Account` | `registerVerifiedExternal(String, String, Role, Instant)`, `grantRole(Role, Instant)`, `getTokenVersion()` |
| `AuthSession` | `open(UUID, long, List, Instant, Instant)` |
| `TokenClaims` | Tür, konu, kapsam ve claim adları sabitleri |
| Ayrıca derlenenler | `Role`, `AccountStatus`, `RoleCode`, `AuthMethod`, `SessionRevokeReason`, `AuthApiPaths` |

Bu imzaları Auth'ta `ConsumerContractTest` kilitler. Bir imzayı bilerek değiştirmek gerekiyorsa önce ilgili servis sahiplerine haber verilir, sonra testle birlikte bu tablo güncellenir.

---

## GÜVENLİK TARAMASI (26 EKİM 2026)

Otomatik taramanın (Checkstyle, PMD, Trivy, Gitleaks) Auth sonuçları:

| Kontrol | Sonuç |
|---|---|
| Checkstyle | 0 ihlal (86 metot adı düzeltildi) |
| PMD | 9 kayıttan 2'si düzeltildi, 7'si üretim kodunda ve karar bekliyor (5 `PreserveStackTrace`, 2 `CloseResource`) |
| Bağımlılık açığı (CVE) | 0 |
| Gitleaks | Temiz |

PMD'de kural kapatma, bastırma ya da test silme yapılmadı. Karar bekleyen kayıtlar güvenlik sınırında olduğu için sebebi eklemek gizli bilgi sızdırabilir.
