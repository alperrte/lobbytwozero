# AUTH BELGELERİ — NEREDE NE VAR

> **Önce [`AUTH_REHBERI.md`](AUTH_REHBERI.md) dosyasını oku.** Auth'a bağlanmak için gereken her şey (token'lar, kod örnekleri, servis çiftleri, silme ucu, olaylar, yerel çalıştırma) orada tek sayfada ve güncel. Bu sayfa yalnız klasör haritasıdır.

**Sahip:** Kerem · **Son güncelleme:** 2026-11-06

---

## KİM NEYİ OKUMALI

| Ben kimim | Oku |
|---|---|
| **Servis sahibiyim, Auth'a bağlanacağım** | [`AUTH_REHBERI.md`](AUTH_REHBERI.md), sonra [`entegrasyon/`](entegrasyon/README.md) içinde kendi servisinin dosyası |
| **Auth'tan bir şey istedim ya da sordum** | [`verilen-cevaplar/`](verilen-cevaplar/README.md) içinde kendi servisinin klasörü |
| **Frontend geliştiriciyim** | [`../docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`](../docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md), [`../docs/api.md`](../docs/api.md) içinde `## AUTH` |
| **Auth'u yerelde çalıştıracağım / açılmıyor** | [`ekip-hizli-baslangic.md`](ekip-hizli-baslangic.md) (rehberin §9'u) |
| **Güvenlik bakıyorum** | [`acik-guvenlik-borclari.md`](acik-guvenlik-borclari.md) |
| **Auth'u geliştiriyorum (Kerem)** | [`kerem-dis-adimlar-ve-canli-test.md`](kerem-dis-adimlar-ve-canli-test.md), [`migration-yazim-kurallari.md`](migration-yazim-kurallari.md), [`anahtar-rotasyonu.md`](anahtar-rotasyonu.md) |
| **Auth'un iç tasarımını öğrenmek istiyorum** | [`AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md`](AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md) (21 Ekim, tarihî) |

## KLASÖR HARİTASI

| Dosya / klasör | Ne |
|---|---|
| [`AUTH_REHBERI.md`](AUTH_REHBERI.md) | **Tek giriş sayfası**, güncel |
| [`servis-sozlesmeleri.md`](servis-sozlesmeleri.md) | Sözleşmelerin gerekçeli tam hali (JWT, silme, servis token'ı, olaylar) |
| [`entegrasyon/`](entegrasyon/README.md) | Her servis için "ne hazır, ne yapacaksın" dosyası ve [cihaz kimliği tasarımı](entegrasyon/cihaz-kimligi-tasarimi.md) |
| [`verilen-cevaplar/`](verilen-cevaplar/README.md) | Servislerin Auth'a sorduklarına açık/kalıcı cevaplar (kapanan talepler temizlendi, git geçmişinde) |
| [`d4-ekip-notlari/`](d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) | Hesap silme ucu: ortak rehber ve kişiye özel notlar |
| [`acik-guvenlik-borclari.md`](acik-guvenlik-borclari.md) | Kapanmamış güvenlik işleri |

> **Neden dosyaları taşımadık:** Başka branch'lerin belgeleri ve kodlardaki yorumlar bu dosyalara `docs-auth/...` yoluyla bağlanıyor. Taşırsak bağlantılar kırılır.
> **Temizlik (2026-11-06):** Faz kapanış raporları, commit/süreç planları, ekip uyum denetimi, boşluk raporu ve kapanmış talep/bilgilendirme dosyaları silindi. Hepsi git geçmişinde: `git log --diff-filter=D --name-only -- docs-auth` ile bulunur, `git show <commit>^:<yol>` ile okunur.

---

## ŞU AN GEÇERLİ ÖNEMLİ DEĞERLER (6 KASIM 2026)

| Konu | Değer |
|---|---|
| Access token ömrü | 60 saniye (`JWT_ACCESS_TTL_SECONDS`) |
| Banlı ya da çıkış yapan kullanıcının görünme süresi | En fazla 90 saniye (60 + 30 saniye saat toleransı) |
| Parola | En az 8 karakter; büyük harf, küçük harf, rakam ve özel karakter; yaygın parola listesi ve desen kontrolü |
| Hesap silme | 14 gün geri alma süresi, sonra servislere silme görevi |
| Güvenlik olayı saklama | 365 gün |
| Silme ucu yazılmış servisler | 9 / 16 (Game, Hardware, Library, Performance, Review, Social, User, Community, Mail) |
| Auth migration aralığı | V61–V70 (V61–V65 kullanımda ve donuk, V66–V70 boş; bkz. `migration-yazim-kurallari.md`) |

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
| `DeviceTokenIssuer` | `(JwtEncoder, JwtSigningKey, JwtProperties, Clock)` tek yapıcısı, `issue(UUID, UUID)` (Hardware token'ı). Hedef seçimi `issue(UUID, UUID, String, String)` ile, **düz String** parametrelerle yapılır |
| `Account` | `registerVerifiedExternal(String, String, Role, Instant)`, `grantRole(Role, Instant)`, `getTokenVersion()` |
| `AuthSession` | `open(UUID, long, List, Instant, Instant)` |
| `TokenClaims` | Tür, konu, kapsam ve claim adları sabitleri |
| Ayrıca derlenenler | `Role`, `AccountStatus`, `RoleCode`, `AuthMethod`, `SessionRevokeReason`, `AuthApiPaths` |

**Kapalı küme kuralı.** Hardware testi yukarıdaki 15 dosyayı (bu 15 sınıf: Account, AuthSession, Role, AccountStatus, RoleCode, AuthMethod, SessionRevokeReason, JwtProperties, JwtSigningKey, TokenClaims, AccessTokenIssuer, ServiceTokenIssuer, DeviceTokenIssuer, JwksController, AuthApiPaths) **tek başına** derler; Auth'un diğer sınıfları sınıf yolunda yoktur. Bu dosyalardan biri küme dışı bir Auth tipine (yeni enum, servis, DTO) bağımlı olursa Hardware'in testi `cannot find symbol` ile kırılır, Auth'un kendi testleri ise geçer. Bu yüzden bu dosyalara yeni bir Auth tipi referansı eklenmez; yeni davranış düz tiplerle (`String`, `UUID`, `Duration`) taşınır. `ConsumerSourceCompilationTest` aynı derlemeyi Auth tarafında yapar ve Auth PR'ında yakalar. Küme değişirse (Hardware kendi listesini güncellerse) test içindeki liste de güncellenir. 2026-10-31'de `DeviceTokenIssuer`'a eklenen `DeviceTokenTarget` bağımlılığı bu kuralı bozmuş ve main'de Hardware testlerini kırmıştı; `String` parametrelere çevrilerek düzeltildi.

Bu imzaları Auth'ta `ConsumerContractTest` kilitler. Bir imzayı bilerek değiştirmek gerekiyorsa önce ilgili servis sahiplerine haber verilir, sonra testle birlikte bu tablo güncellenir.

---
