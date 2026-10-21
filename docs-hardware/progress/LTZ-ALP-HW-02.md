# LTZ-ALP-HW-02 — Donanım Model Kataloğu, Arama ve Kampanyalar

Sorumlu: Alper. Başlangıç: 2026-10-21. Durum: DONE — DEFERRED_AUTH; 3/3.

Kullanıcı HW-01 PDA kapanışı sonrasında ikinci ana göreve geçilmesini açıkça onayladı.
Önceki dört dokümantasyon kapanış değişikliği korunur. Git yazma işlemi yapılmaz.

- [x] HW-10 — Donanım ürün kataloğu
- [x] HW-11 — Donanım arama ve filtreleme
- [x] HW-14 — Donanım kampanyaları

İlk incelemede V2'nin yalnız seçim için asgari ürün/marka tabloları ve profil
sahipliği güvenlik sınırı içerdiği doğrulandı. Katalog yönetimi için mevcut onayda
bulunmayan küresel yazma izni ve kampanya veri kaynağı kararı gerekir.
[Somut sözleşme önerisi](../decisions/HW02-catalog-campaign-proposal.md) hazırlandı.
Onaya bağlı iş beklerken kurallar, mevcut kod ve test altyapısı okunuyor.

LTZ_PDA_Alper.md repository içinde bulunamadı; kök master 5/17 checklist'i temel alınır.
Python komutu mevcut değil; genel gereksinim DOCX metni .NET Zip/XML ile okunuyor.
Yeni test/final pre-push/Sonar henüz çalıştırılmadı. Önceki HW-01 kalite sonuçları
bu ana görev için PASS sayılmaz.

## Başlangıç doğrulaması

`mvn -f backend/pom.xml -pl services/hardware-service -am verify -B -ntp`
2026-10-21 01:34:51 +03:00 tarihinde exit 0: 58 test PASS, 0 failure/error/skipped.
JaCoCo 321/332 satır, %96,69. Java 25.0.4.1, Maven 3.9.16, PostgreSQL 17.11
Testcontainers; mevcut V1/V2 Flyway ve JPA validate başarılı. Gerçek ENV açılmadı.
Sandbox Docker/Maven erişimi kısıtlı olduğundan aynı test komutu izinli erişimle
çalıştırıldı; DB reseti veya başka servis değişikliği yapılmadı.

Onaydan sonraki ilk uygulama: HW-10 marka/model yönetimi, opsiyonel ilişki,
markadan bağımsız tekillik, sürüm/transaction ve arşiv referans koruması.
Testler: normal kullanıcı yazma reddi, typed olmayan principal, anonim/CSRF,
gövde boyutu, invalid/unknown alanlar, mükerrer kayıt ve rollback, eski sürüm,
arşivli model/marka ve mevcut profil snapshot'larının korunması, audit atomikliği.
HW-10 kanıtlanmadan HW-11 ve HW-14 geliştirmesine geçilmeyecek.

Master §0.4 / SECURITY.md §19 gereği iki onay sorusu iletildi; cevap henüz yok.
Bu kararların gerektirdiği production kodu, API veya migration yazılmadı.

## HW-10 tamamlandı — 2026-10-21

Kullanıcı iki sözleşmeyi de onayladı. 10 katalog endpoint'i, CRUD/arşiv DTO'ları,
HardwareBrandService/HardwareCatalogService/HardwareCatalogMapper, typed actor +
CatalogWriteAccess sınırı ve append-only audit repository uygulandı.
V3 yalnız status/version CHECK ve indeksleri, audit tablo/immutability trigger'ını
ekler; V1/V2 değiştirilmedi. Yönetim yazmaları aktör başına dakikada 60 ile sınırlı;
audit ve iş mutasyonu atomik, limit aşımı rollback ve 429 + Retry-After üretir.
Arşivli ürün/marka yeni profil seçiminde reddedilir, eski snapshot/ref korunur.
Model category immutable; kullanılmakta olan ürün/marka bağını kaldırma FK ile 409.

İlk doğrulama: 90 testten 2 hata; markalı model create aşamasında primitive version
yüzünden merge kullanılıyor ve başlangıç version artıyordu. Nullable JPA @Version
ile yeni entity persist olarak algılanır; create version=0 sözleşmesi sağlandı.
Son doğrulama: Maven verify exit 0, 90 PASS, 532/546 satır (%97,44),
2026-10-21 01:44:06 +03:00. logs/hw02-hw10-verified.log.
32 yeni HTTP/DB senaryosu: 12 kategori, markasız/multi-brand, normalized tekillik,
CSRF/anonim/yanlış principal/normal kullanıcı, gövde/sayfa sınırları, eski sürüm,
eşzamanlı writer, arşiv ve profil referans koruması, audit değişmezliği/rate rollback.
V1/V2/V3 Flyway ve Hibernate validate PostgreSQL 17.11 üzerinde PASS.
docs/api.md HARDWARE, DB/ER/mimari kayıtları eşleştirildi. Gerçek Auth DEFERRED_AUTH.
HW-10 kutusu işaretlendi; sıradaki HW-11. Final pre-push/Sonar henüz NOT_RUN.

## HW-11 tamamlandı — 2026-10-21

Mevcut GET /catalog/products endpoint'i geriye uyumlu opsiyonel filtrelerle genişledi:
model alt metni, kategori, aktif üretici UUID/adı, dört sayısal teknik alt/üst sınırı
ve dört metinsel teknik alan. Criteria SQL parametreli; identifier ve sort kullanıcı
girdisi değildir. LIKE özel karakterleri literal escape edilir, boş q/brand filtre
uygulamaz. Bilinmeyen teknik alan eşleşmez; aynı üretici hem id hem ad koşulunu sağlar.
Sayfa toplamları ve marka hydration REPEATABLE_READ snapshot'ında; marka join'inde
distinct ve sabit normalizedModel/id sıralama korunur. V4 dört partial teknik indeks
ekler; V1–V3 değişmedi ve extension/harici arama bağımlılığı eklenmedi.

Maven verify exit 0: 117 PASS, 576/590 satır (%97,63), 2026-10-21 01:47:00 +03:00.
27 yeni search testi, PostgreSQL 17.11 / V1–V4 Flyway/JPA validate PASS.
Aralık tersliği, null/archived ayrımı, injection/literal LIKE, AND filtreleri,
sayfa/sayaç ve indeks varlığı doğrulandı. docs/api.md güncellendi.
HW-11 [x]; HW-14 başlıyor. Final pre-push/Sonar hâlâ NOT_RUN.

## HW-14 yerel uygulama/test tamamlandı — 2026-10-21

HardwareCampaignController/Service/Mapper/Repository/Entity ve request/response
record'ları eklendi; beş endpoint docs/api.md içinde kayıtlı. V5 kampanya tablosu,
ürün FK, fiyat/zaman/status/version CHECK'leri ve aktif pencere indekslerini ekler.
Kayıt yalnız kaynak ve gözlem zamanı beyanıyla gelir; sağlayıcı, scraping veya
otomatik fiyat doğrulaması yoktur. Normal okumada aktif ürün/kampanya ve
startsAt <= şimdi < endsAt şartı, sayfalama ve ürün filtresi uygulanır.
Yönetim mutasyonları onaylı uygulama izni, sürüm, CSRF ve ortak audit/rate limit sınırında.
Arşivleme fiziksel silme yapmaz. Diğer ana görevler/servisler geliştirilmedi.

Son incelemede NFKC dönüşümünün sonrasında da HTML/kontrol karakteri kontrolü
eklendi; Unicode kaynak URL'nin ASCII kodlanınca 2048'i aşması ve XXX para birimi
reddedilir. Testler bu durumları, fiyat hassasiyetini ve yanlış değerleri kapsar.
Yeni uygulama güvenlik sınırı doğrudan servis portunda da sınandı.

Son kod Maven verify exit 0: 134 PASS, 688/702 satır (%98,01),
2026-10-21 01:53:29 +03:00; logs/hw02-final-local-verify.log.
15 kampanya, 33 katalog, 27 arama, 1 doğrudan güvenlik ve önceki 58 test.
PostgreSQL 17.11 / V1–V5 Flyway / Hibernate validate PASS; V1/V2 değişmedi.
Body/URL/mass assignment, normal kullanıcı ve yanlış principal, CSRF,
indirim/para birimi/zaman, görünürlük penceresi, audit ve eşzamanlı version
kuralları doğrulandı. Son temiz build de 133 testle önceki düzeltme öncesi PASS idi;
son ek Unicode negatif senaryosuyla nihai sayı 134 oldu.

HW-10/HW-11/HW-14 kutuları [x], toplam 10/17. Ana task [ ] ve QUALITY_BLOCKED:
HW-02 final pre-push/Sonar henüz NOT_RUN. Master §8 gereği son kod ve dokümanlar
hazırlandıktan sonra bu iki komut için kullanıcıdan tetikleme izni istenecek.
İki kapı geçmeden teslim raporu/READY/PDA kapatma önerisi/feature mesajı verilmeyecek.

Kalite tetikleme izni oturum geçmişiyle tekrar kontrol edildi: kullanıcı daha önce
“İki kalite komutunu çalıştır” diyerek aynı hardware-service pre-push ve scan dev
komutlarını açıkça onayladı; izin geri çekilmedi ve kapsam değişmedi.
Bu nedenle tekrar onay istenmeden aynı komutlar HW-02 son kodu için çalıştırılıyor.
Token/profil gerçek değerleri açılmayacak; mevcut runner içeride kullanacak.

## Sonar kapısı düzeltmeleri

İlk Sonar analizi Reliability C ve yeni issue kapısı nedeniyle FAIL oldu;
analiz b07f63aa-3816-4ec8-83ef-8e5e1ac757c9. 9 bulgu hardware kodunda:
nullable authentication, uzun entity constructor/metotları, generic throws,
record pattern ve test lambda'sında ikinci runtime çağrısı. Security A,
Maintainability A, duplication %0; hotspot/vulnerability/kritik 0.
Kalite raporu gerçek FAIL sonucunu korur. Task READY/PDA/commit'e hazır değildir.

Nullable authentication için açık guard; uzun imzalar için üç küçük domain
record'u; generic throws kaldırılması, record pattern ve test lambda düzeltmesi
uygulandı. Bazı noktalar HW-01'in ortak kullandığı modellerdir; yalnız bu gerçek
kalite kapısının gerektirdiği en küçük hardware düzeltmeleri yapıldı.
JSON API/migration/sahiplik davranışı değişmedi; eski 58 regresyon da tekrar koşacak.
Önceki pre-push PASS tarihsel kanıt; düzeltilmiş kod için iki kapı tekrar çalışıyor.

## Nihai teslim — READY_FOR_PDA — DEFERRED_AUTH

Son kod pre-push: 134 PASS, %98,02 (694/708 satır), exit 0,
2026-10-21 02:01:49 +03:00. Sonar öncesi bütün backend 152 PASS / 18 modül;
yerel ağırlıklı coverage %93,68. Sonar scan dev exit 0 / SONAR SCAN PASSED;
Security/Reliability/Maintainability A, Quality Gate ve overall targets PASS.
Analiz 1d274361-0d59-4aee-9e54-3303c0ef52e5, 2026-10-21 02:04:53 +03:00;
scan bitişi 02:05:11 +03:00; overall coverage %89,2, duplication %0,
new coverage %92,3, new duplication %0, new issue 0. Son API kontrolü:
açık issue/vulnerability/hotspot 0. Atanmış Sonar way politikası VERIFIED.
Başarısız ilk analiz ve 9 düzeltme kalite günlüğünde korunur; bastırma/exclusion yok.

[Teslim raporu](../reports/LTZ-ALP-HW-02-delivery.md) iki kapı doğrulandıktan sonra
oluşturuldu. Merkezi API/DB/ER/mimari, master ve kalite kayıtları eşleştirildi.
3/3 ve toplam 10/17 alt görev [x]; ana görev kutusu kullanıcı PDA kapanışını
bildirmediğinden [ ] kalır. Gerçek Auth DEFERRED_AUTH; production-ready değildir.
Git add/commit/push veya PDA durum değişikliği yapılmadı. Sonraki ana taska
başlanmadı; tek ayrıntılı feature mesajı ve PDA kapatma bildirimiyle STOP.

## PDA kapanışı ve HW-03 geçişi — 2026-10-21

Kullanıcı “Evet, HW-02’yi PDA’da kapattım” bildirimini verdi ve HW-03'e geçişi
onayladı. Master ana task 02 [x], durum DONE — DEFERRED_AUTH.
Kullanıcının 6756210 commit'i salt okunur git log/show ile doğrulandı; 60 dosyalık
HW-02 teslimini içerir. Yeni görev HW-08 ile başlar.
Bu sırada pre-push/backend-check.mjs, menu.test.mjs, pre-push.md, quality.mjs ve
quality.test.mjs üzerinde bu çalışmaya ait olmayan değişiklikler bulundu; korunur.
Bu harici runner değişiklikleri HW-02 kapsamında doğrulanmış sayılmaz.
