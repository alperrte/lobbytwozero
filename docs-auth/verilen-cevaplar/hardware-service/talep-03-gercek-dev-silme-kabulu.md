# TALEP 03 — GERÇEK DEV ORTAMINDA HESAP SİLME KABULÜ

**Talep kodu:** HW-AUTH-REQ-03 · **Kimden:** Alper (hardware-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26
**Durum:** CEVAPLANDI — Auth değişiklikleri main'de (PR #19); ortak DEV denemesi E2E betiği düzeltilince (aşağıdaki "Senden ne bekliyorum")

---

## NEYDİ

Hardware hesap silme isteğine katılıyor. Alper kendi tarafını gerçek Auth, Gateway ve Hardware ile geçici veritabanında denedi ve 16 kontrolün hepsi geçti. Auth'un görevi Hardware'dan 204 aldı, görev CONFIRMED oldu, profil silindi, ikinci çağrı da 204 verdi, eski token'la yeniden yazma reddedildi. Diğer hedefler PENDING kaldı. Şimdi bununla birlikte ortak bir DEV denemesi istiyor.

## NE SORULDU

- Güncel Hardware imajıyla birlikte sentetik bir hesapla gerçek silme komutunu birlikte çalıştırabilir miyiz
- Diğer servislerin eksik onayları atlanmayacak ve sahte başarıyla kapatılmayacak
- Hesap silme yetkisi ve sözleşme aynı kalıyor mu

## BENİM CEVABIM

**EVET YAPALIM. AMA ÖNCE BİZİM DEĞİŞİKLİKLER MAIN'E GİRMELİ.**

Senin kendi doğrulaman çok iyi. Hardware tarafı için ek bir şey istemiyorum.

Auth tarafında şu an durum şöyle. Silme ucunu yazan servisler beş tane: Social, Game, Library, Review ve Hardware. Yani 16 hedeften 5'i hazır. Kalan 11 hedef bekliyor ve bekleyecek. Onlar için görev PENDING kalır ve hesap DELETION_REQUESTED durumunda durur. Hiçbirini sahte başarıyla kapatmıyoruz. Bunu bizim kuralımız zaten engelliyor.

Sözleşme aynı. Auth imzalı svc+jwt gönderiyor. Hedef tek ve hardware-service. Konu tam olarak svc:auth-service. Kapsam account:erase. Hesap kimliği yoldaki kimlikle aynı. Gateway'e açılmıyor. Hiçbir şey değişmedi.

## DİKKAT — BİR ŞEY DEĞİŞTİ VE SENİN BETİĞİNİ ETKİLİYOR

Biz silme isteğine **14 günlük geri alma süresi** ekledik. Kullanıcı silmeyi onaylayınca hesap hemen kapanıyor ama silme görevleri 14 gün sonra başlıyor. Bu sürede kullanıcı parolasıyla vazgeçebilir. Süre bitince Auth'taki kişisel veri hemen anonimleşiyor, servislerin onayını beklemeden.

Senin `ops/hardware-auth-e2e.mjs` betiğin silme onayından hemen sonra görevin CONFIRMED olmasını bekliyor. Bizim değişiklik main'e girince görev 14 gün sonra başlayacak ve betik zaman aşımına düşecek.

**Çözüm tek satır.** Betikte Auth'u başlattığın ortama şunu ekle:

`AUTH_DELETION_GRACE_PERIOD: 'PT0S'`

Bu sadece test ortamı için. PT0S bekleme yok demek. Prod'da 14 gün kalacak. Betik senin olduğu için dokunmadım.

## 17 DAKİKALIK FENCE İLE 14 GÜNÜN ARASI

Çakışmıyor. 14 günlük bekleme süresinde hesap zaten kapalı. Oturumlar iptal, eski token'lar 90 saniyede ölmüş oluyor. Görevler bekleme bitince başlıyor. Senin 17 dakikalık fence'in o zaman devreye giriyor. Yani sorun yok.

## ORTAK DEV DENEMESİ NASIL OLACAK

1. Bizim değişiklikler main'e girer
2. Sentetik bir test hesabı açılır, gerçek kişisel veri yok
3. Auth'ta bekleme süresi PT0S ile başlatılır
4. Silme istenir ve onaylanır
5. Beklenen sonuç: Hardware görevi CONFIRMED. Ayakta olan ve ucu yazılmış diğer servisler de CONFIRMED. Ucu henüz olmayan 11 hedefin görevi PENDING. Hesap DELETION_REQUESTED kalır
6. Tekrar komut 204 verir, Hardware'da kişisel satır kalmamış olur
7. Sonucu ikimiz kayda geçiririz

Belge notu: "diğer 15 ACK" ifaden artık "kalan 11 ACK" olmalı.

## SENDEN NE BEKLİYORUM

- E2E betiğine `AUTH_DELETION_GRACE_PERIOD: 'PT0S'` eklemek
- Belgedeki 15 sayısını 11'e çevirmek
- Betik düzelince ortak denemenin gününü birlikte seçmek (Auth değişiklikleri main'de)

## NEREDEN BAKARSIN

- Silme akışının tamamı: `docs/api.md` içinde "Hesap silme"
- Servis tarafı sözleşme: `docs-auth/servis-sozlesmeleri.md` bölüm 3
- Ekip notu: `docs-auth/d4-ekip-notlari/05_ALPER_hardware_recommendation_performance_ve_repo_sahibi.md`
