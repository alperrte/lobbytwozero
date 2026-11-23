# HARDWARE-SERVICE: ALPER'İN DÖRT İSTEĞİ İÇİN KARŞILIK TABLOSU

**Sahip:** Alper · **Hazırlayan:** Kerem (auth-service) · **Tarih:** 2026-10-29 · **Kaynak:** `docs-hardware/integration/auth/auth-service-talepleri/auth-requests-01..04.md` (Hardware dalı)

Her istek için: ne istendi, Auth'ta ne yapıldı, nerede yazılı, ne açık kaldı ve kimin işi. Durum main'e göre (PR #25 ve #26).

| İstek | Alper'in istediği | Auth'ta yapılan | Nerede yazılı | Açık kalan |
|---|---|---|---|---|
| **01** Token iptali ve hesap durumu | Taşıma ve eskime politikasını Kerem seçsin; HTTP olacaksa `docs/api.md`'de yayımlansın | Seçildi: yeni uç ve sorgu yok, iptal 60 sn token ömrü + 30 sn toleransla en çok **90 sn** içinde yayılır. İsteğe bağlı olay `AccountStatusChanged` (yalnız yönetici durum değişikliği, outbox) hazır, hedef listesi boş | `docs/api.md` "Token iptali ve hesap durumu yayılımı (politika)"; `verilen-cevaplar/hardware-service/talep-01-*`; `servis-sozlesmeleri.md` bölüm 3.5 | Hardware: belgedeki "15 dakika" ifadesini "90 sn" yapmak. Olay istenirse alıcı uç yazılır, `hardware` hedef olarak eklenir |
| **02** Cihaz kimliği | Credential tipi, süre, iptal, anahtar dağıtımı Kerem tarafından tanımlanıp `docs/api.md`'ye yazılsın | **Uygulandı** (V67): bağlama, listeleme, iptal, `dev+jwt` token ucu; gerçek Auth ve Gateway ile denendi | `docs/api.md` "Masaüstü cihaz kimliği (hardware-service)"; `docs-auth/entegrasyon/hardware-service.md` (akış, çözülmüş token, kontrol listesi, hata tablosu) | Hardware `dev+jwt` doğrulayıcısı yazıldı (HW-07, main'de) ve Auth'un ürettiği token'la uyumlu. **Kalan: PERF-01** aktif cihaz/sahiplik API'si Alper'in (Performance) işi, Auth'a devredilmedi |
| **03** Gerçek DEV silme kabulü | Ortak DEV denemesi ve kalan servis ACK'leri | Auth tarafı hazır, yeni uç istenmedi. Hardware katılımcısı doğrulanmış | `docs/api.md` "Hesap silme"; `talep-03-*`; `docs-auth/d4-ekip-notlari/` | Alper'in E2E betiği düzeltildi (`PT0S`, 60 sn TTL) ve gerçek üç jar ile izole denemede geçti. **Tüm servislerle ortak DEV denemesi yapılmadı;** diğer servislerin silme uçları kendi sahiplerinde (main'de Game, Hardware, Library, Review var) |
| **04** DEBUG/TRACE logları | Logger politikası; Kerem risk ve üretim ayarı kararını kaydetsin | Düzeltildi ve test edildi (`LoggingPrivacyPinTest`); hassas loglar INFO'ya sabitlendi, main'de etkin | `acik-guvenlik-borclari.md` B20; `talep-04-*` | Alper: Google/Steam testlerini DEBUG açıkken bir kez koşturmak. **Prod'da DEBUG/TRACE açılmaz** (dağıtım kuralı) |

## BİLİNMESİ GEREKEN İKİ AYRINTI

- **503 ve `Retry-After`:** Hardware kısmi temizlikte `503` + `Retry-After` dönerse Auth bunu onay saymaz ve görevi yeniden dener. Auth `Retry-After` başlığını okumaz, kendi geri çekilmesini kullanır (1 dk'dan en fazla 6 saate). Çelişki yaratmaz, ama başlığa uyulacağını varsaymayın.
- **Yeni HTTP sözleşmesi:** Hardware'in "yeni HTTP/JSON sözleşmesi önerilmiyor" tespiti Auth ile uyumludur. Silme sözleşmesi (`DELETE /internal/v1/accounts/{accountId}`, `svc+jwt`, `aud=hardware-service`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = yoldaki hesap) değişmedi.
