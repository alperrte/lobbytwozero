# TALEP 04 — DEBUG VE TRACE AÇIKKEN OAUTH FORMLARININ LOGLANMASI

**Talep kodu:** HW-AUTH-REQ-04 · **Kimden:** Alper (hardware-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26 · **Düzeltme tarihi:** 2026-10-27
**Durum:** KAPANDI — Auth'ta düzeltildi, test edildi ve main'de etkin (PR #19)

---

## NEYDİ

Alper'in ilk tam backend koşusunda Auth'un Google ve Steam giriş testlerinden ikisi düştü. Sebep genel DEBUG bayrağı açıkken Spring'in HTTP istemcisi giden formları loglaması. Logda sadece test için üretilmiş sahte OAuth kodları görüldü. Gerçek bir sızıntı olmadı. Ama aynı ayar gerçek ortamda açılsaydı sorun olurdu.

## NE SORULDU

- DEBUG ya da TRACE açık olsa bile OAuth formları, kodlar, state ve kimlik bilgileri loglanmasın
- Bunun için Auth bir logger ya da HTTP istemci kuralı koysun
- Google ve Steam testleri DEBUG açıkken de geçsin

## BENİM CEVABIM

**HAKLISIN. DÜZELTTİM. VE SORUN SENİN GÖRDÜĞÜNDEN DAHA GENİŞMİŞ.**

Önce senin hatayı birebir yeniden ürettim. `DEBUG=true` ve `LOG_LEVEL=DEBUG` ile Google ve Steam testlerinden birer test düştü, tıpkı sende olduğu gibi.

Sızdıran yerler şunlar çıktı:

- **HTTP istemcisi** (`org.springframework.web.client`): giden OAuth formları. Senin gördüğün bu
- **Hibernate varlık dökümü** (`org.hibernate.orm.core`): DEBUG'ta `Account` kaydının tamamını yazıyor. Yani e-posta, parola özeti ve token sürümü loga düşüyor. Bu senin görmediğin ve daha ciddi olan kısım
- **Hibernate ve JDBC parametreleri** (TRACE): SQL'e bağlanan e-posta, özet ve kod değerleri
- **PostgreSQL sürücüsü**: sorgu ve parametre izleri
- **Spring MVC** (TRACE): kontrolcü argümanları (OAuth callback'te code ve state), istek parametreleri, yanıt gövdeleri

Gerçek bir ortamda DEBUG açıldığında bunların hepsi loga düşebilirdi. Senin testin bir kapıyı yakaladı, arkasında başka kapılar da vardı.

## NE YAPTIM

1. Bu loglayıcı paketlerini Auth'un ayar dosyasında açıkça INFO'ya sabitledim. Spring'de açıkça yazılmış logger ayarı genel seviyeyi ezer. Yani genel DEBUG ya da TRACE açılsa bile bunlar INFO kalıyor
2. En kötü durumu denedim. `LOG_LEVEL=TRACE`, `LOG_LEVEL=DEBUG` ve `DEBUG=true` ile Google ve Steam testleri **üçünde de 22/22 geçti**
3. Kalıcı bir test ekledim (`LoggingPrivacyPinTest`). Dokuz logger'ın açıkça INFO olduğunu denetliyor. Bir pin'i geçici olarak kaldırıp testin gerçekten düştüğünü gördüm, sonra geri koydum

Kök seviyeyi TRACE yapan bir test eklemedim, çünkü bu aynı süreçteki diğer testlerin log durumunu bozabilirdi. Onun yerine pin'lerin yazılı olduğunu denetliyorum. En kötü durum doğrulaması için elle çalıştırma komutu test dosyasının açıklamasında var.

## SENDEN NE BEKLİYORUM

- Senin kalite betiğinin genel DEBUG bayrağını test sürecinden ayırması kalabilir. O senin tarafın. Ama güvenlik artık ona bağlı değil
- Düzeltme main'de: Google ve Steam testlerini DEBUG açıkken bir kez koştur ve sonucu paylaş
- **Prod'da DEBUG ya da TRACE açılmasın.** Pin'ler korumayı sağlıyor ama bu ayrı bir kural olarak dağıtım belgesine de yazılacak

## NEREDEN BAKARSIN

- Pin'ler: Auth'un `application.properties` dosyasında `LOGGING` bölümü
- Test: `LoggingPrivacyPinTest`
- Kayıt: `docs-auth/acik-guvenlik-borclari.md` içinde B20
