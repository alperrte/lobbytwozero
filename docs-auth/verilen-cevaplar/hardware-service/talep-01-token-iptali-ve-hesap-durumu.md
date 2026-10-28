# TALEP 01 — HESAP DURUMU VE TOKEN İPTALİ

**Talep kodu:** HW-AUTH-REQ-01 · **Kimden:** Alper (hardware-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26
**Durum:** KAPANDI — cevap verildi, bizim tarafta ek geliştirme yok

---

## NEYDİ

Hardware gelen kullanıcı token'ını kendi içinde doğruluyor. Ama biri banlanınca, çıkış yapınca ya da rolü alınınca elindeki eski token süresi dolana kadar hâlâ geçerli görünüyor. Alper bu gecikmenin ne kadar olduğunu ve bunun için bir kuralın olup olmadığını bilmek istedi.

## NE SORULDU

- Banlanan ya da çıkış yapan kullanıcı Hardware'da en fazla ne kadar süre daha işlem yapabilir
- Bunu kısaltmak için Auth bir olay mı yayacak yoksa Hardware Auth'a mı soracak
- Auth'a ulaşılamazsa Hardware ne yapacak

## BENİM CEVABIM

**EN FAZLA 90 SANİYE.**

Access token ömrü 60 saniye. Üstüne 30 saniye saat toleransı var. Yani banlanan biri en kötü ihtimalle bir buçuk dakika daha eski token'ıyla görünür. Sonra token ölür ve yenisini alamaz çünkü Auth'ta oturumları anında iptal oluyor.

Olay yok. Sorgu ucu da yok. Hardware yeni bir şey kurmasın.

Auth'a ulaşılamazsa şu zaten senin tarafta var. Anahtar önbellekte duruyorsa doğrulamaya devam et. Bilinmeyen anahtar gelirse reddet. Yani güvenli tarafta kal.

## NEDEN 15 DAKİKA DEĞİL 60 SANİYE

Belgende "Auth en fazla 15 dakika" yazıyor. O doğru ama sadece izin verilen EN YÜKSEK ayar sınırı. Gerçek varsayılan önce 5 dakikaydı. Ben 24 Ekim'de 60 saniyeye indirdim.

Sebep basit. Banlanan hesabın diğer servislerde açık kalma süresi tam olarak token ömrü kadar. Olay sistemi ya da sorgu ucu kurmadan bunu kısaltmanın tek yolu token ömrünü kısaltmak. Bedeli şu. Tarayıcı dakikada bir yenileme yapıyor, Auth'a biraz daha yük biniyor. Bunu kabul ettim.

Prod'da da 60 saniye kalacak. Bu değer değişirse sana önceden haber veririm.

## SIFIR GECİKME İSTERSEN

O ayrı bir proje. Ya Auth bir iptal listesi yayacak ya da servisler her istekte Auth'a soracak. İkisi de bütün servislere iş çıkarır. Şimdilik böyle bir ihtiyaç görmüyorum. Katalog yazma gibi kritik işlemler için 90 saniye yetmiyor derseniz bunu konuşuruz.

## SENDEN NE BEKLİYORUM

- Belgendeki "15 dakika" ifadesini "90 saniye (60 sn token + 30 sn tolerans)" yap
- Kabul kriterindeki "anlaşılmış süre içinde reddedilir" kısmına 90 saniyeyi yaz
- Kendi tarafında yeni bir polling ya da cache kurma

## NEREDEN BAKARSIN

- Token ömrü ayarı: `JWT_ACCESS_TTL_SECONDS` (varsayılan 60)
- Genel gecikme kaydı: `docs-auth/acik-guvenlik-borclari.md` içinde B2
- Servis sözleşmesi: `docs-auth/servis-sozlesmeleri.md`
