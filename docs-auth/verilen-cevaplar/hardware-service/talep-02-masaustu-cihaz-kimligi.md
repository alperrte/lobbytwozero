# TALEP 02 — MASAÜSTÜ CİHAZ KİMLİĞİ

**Talep kodu:** HW-AUTH-REQ-02 · **Kimden:** Alper (hardware-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26
**Durum:** KARAR VERİLDİ — B yolu seçildi · **GELİŞTİRMEYE ŞU AN KAPALI**, sıraya alındı

---

## NEYDİ

Masaüstü uygulaması donanım bilgisini Hardware'a gönderecek. Bunun için `POST /api/v1/hardware/profiles/{profileId}/sync/snapshots` ucu var. Bu uç kullanıcı token'ıyla açılamıyor çünkü Alper ayrı ve dar yetkili bir cihaz kimliği istiyor. Hardware tarafı hazır. Eksik olan Auth'un böyle bir kimliği vermesi.

## NE SORULDU

- Cihaz için ayrı bir kimlik bilgisi tipi olacak mı
- Bu kimlik nasıl verilecek, ne kadar yaşayacak, nasıl yenilenecek, nasıl iptal edilecek
- Kimlik hangi hesaba ve hangi cihaza bağlı olacak, hangi yetkiyi taşıyacak
- Aktif cihaz listesini Performance servisi tutacak. O ve Auth nasıl anlaşacak

## BENİM CEVABIM

**B YOLUNU SEÇTİM. CİHAZ KİMLİĞİNİ AUTH VERECEK.**

Üç yol vardı.

- **A:** Masaüstü uygulaması kullanıcı girişiyle token alır, cihazı Hardware kendi kaydeder. Auth'ta iş yok ama yetki dar olmaz.
- **B:** Auth cihaza ayrı bir kimlik verir. Cihaz bununla ayrı türde kısa ömürlü token alır. İptal Auth'ta olur.
- **C:** Hardware cihaz anahtarını kendi yönetir. Auth'ta iş yok ama kimlik ve iptal Hardware'a kalır.

B'yi seçtim. Yönü şöyle.

1. Kullanıcı normal oturumuyla "bu cihazı bağla" der
2. Auth cihaza uzun ömürlü bir kimlik bilgisi verir. Auth sadece özetini saklar
3. Cihaz bununla kısa ömürlü bir cihaz token'ı alır. Türü kullanıcı token'ından farklı olur. Hedefi hardware-service olur. Yetkisi sadece `hardware:sync:write` olur. İçinde hesap ve cihaz kimliği olur
4. Cihaz iptal edilirse Auth'ta kayıt kapanır ve yeni token verilmez
5. Cihazın aktif olup olmadığı ve kime ait olduğu kararı Performance'ta kalır, o PERF-01 işi

## NEDEN B

- **Dar yetki.** Cihaz token'ı sadece senkronizasyon yazabilir. Çalınırsa başka bir şey yapamaz
- **Karışmaz.** Kullanıcı token'ıyla cihaz token'ı farklı tür. Biri diğerinin yerine geçemez
- **İptal tek yerde.** Cihaz kaybolursa ya da kullanıcı hesabı silinirse kimlik Auth'ta kapanır
- **Kimliğin sahibi Auth olmalı.** Kimlik doğrulama başka serviste dağılırsa güvenlik kuralları servis servis farklılaşır. Biz bunu zaten servis token'larında bu şekilde çözdük, Auth imzalıyor servisler doğruluyor

A yolu işi bana yüklemiyor ama yetkiyi geniş bırakıyor. C yolu da cihaz güvenliğini Hardware'a yüklüyor. İkisini seçmedik.

## GELİŞTİRMEYE AÇIK MI

**HAYIR. ŞU AN KAPALI.** Sebepleri şunlar.

- Masaüstü uygulaması yok. Alper'in kendi belgesi de "desktop apply açılmadan önce" diyor
- Performance servisinin aktif cihaz listesi API'si yok. Auth tarafını yazsam bile uçtan uca denenemez
- Ben şu an önce mevcut işleri kapatıyorum. Silme geri alma süresi, e-posta değiştirme, servis kimlikli uçlar ve tarama düzeltmeleri henüz main'de değil
- Tasarım yazılı karar ister. Kimlik bilgisi tipi, süreler, yenileme ve iptal kuralları

**Ne zaman açılır:** Alper masaüstü tarafına ne zaman başlayacağını söylediğinde. O zaman önce kısa bir tasarım kararı yazarım, sonra migration ve uçları yaparım. Auth migration aralığında V67 ile V70 arası boş. Bu iş bir ya da iki tanesini kullanır.

## ŞİMDİLİK SENDEN NE BEKLİYORUM

- Hardware `sync/snapshots` ucu fail-closed kalsın. Şu an zaten öyle
- Auth'ta cihaz uç adı, claim adı ya da kapsam adı varmış gibi kod yazma. Henüz hiçbiri yok
- ADMIN cihaz yerine geçemez. Bu kuralı sen de yazmışsın, aynen kabul
- Masaüstü başlangıç tarihi belli olunca bana haber ver

## NEREDEN BAKARSIN

- Açık özellik kaydı: `docs-auth/acik-guvenlik-borclari.md`
- Servis token mantığı (örnek olarak): `docs-auth/servis-sozlesmeleri.md` bölüm 3
