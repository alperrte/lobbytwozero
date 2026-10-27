# TALEP 02 — SERVİS TOKEN'I, SİLME, DURUM OLAYI VE JWKS SORULARI

**Kimden:** Nisa (community-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-28
**Durum:** CEVAPLANDI — iki konu Alper'de, bir konu Auth'ta planlı

---

## NEYDİ

Nisa 6 soru göndermiş. Servis token'ı, hesap silmede anonimleştirme, dev'de silme testi, durum olayı ve JWKS süreleri.

## ÖNCE ŞUNU BİLİN

Aşağıda anlattığım yeni şeyler (14 gün bekleme, ek JWKS anahtarı, servis token ucu) `feature/auth-service` dalında hazır ama **main'e ve dev'e henüz girmedi**. PR açılıp birleşince devreye girer. Tarih vermiyorum çünkü Alper onayı gerekiyor

## CEVAPLAR

**1. Servis token'ı başka kapsamlara ve çağıranlara açılacak mı, `events:ingest` uygun mu**
Şu an Auth'tan servise giden token sadece `account:erase` kapsamında ve çağıran hep `svc:auth-service`. Ters yönde (servis Auth'a gelirken) bir token ucumuz var ama sadece admin için. Genişletmeye açığız ama **hiçbir servise varsayılan imtiyaz vermiyoruz**. Güvenlik ve Auth önde. Her çağıran için ayrı ayrı tanım yapılır: kim, hangi servise, hangi kapsamla. Listede olmayan hiçbir şey çalışmaz. `events:ingest` adı bizim `kaynak:eylem` biçimine uyuyor, uygun. Hangi servis hangi kapsamı neden istiyor bana yaz, tek tek bakıp ekleyeceğim

**2. Community admin ve notification'a giderken token'ı nasıl alır**
Auth'un token ucundan. Community için ayrı bir istemci kimliği ve gizli anahtar tanımlanır, gizli anahtar ortam değişkeninden gelir, koda ve repoya yazılmaz. Şu an böyle bir istemci sadece admin için var ve token'ı sadece Auth'a gidebiliyor. Community→admin/notification için madde 1'deki gibi tanım eklenecek. Başka gizli bir yol yok

**3. Anonimleştirme yeterli mi, sabit silinmiş hesap kimliği, `[removed]`, rapor saklama süresi**
Auth kendi tarafında hesabı anonimleştiriyor. Sabit kimlik ve `[removed]` kullanman Auth sözleşmesiyle çelişmiyor. Ama raporların ne kadar saklanacağı ve hukuki gerekçesi **benim kararım değil, Alper bey sorumlu**. Ona sor, o netleştirsin. Netleşene kadar raporları anonim saklayıp süreyi ayarlanabilir tut

**4. Dev'de birlikte silip CONFIRMED görebilir miyiz**
Görebiliriz ama iki şart var. Silme artık 14 gün bekleme süresiyle başlıyor, dev'de 14 gün beklemek anlamsız. Dev'de bu süreyi sıfıra (`PT0S`) çekeceğiz, sadece dev'de, canlıda 14 gün kalır. Böylece silince görev hemen çalışıyor. İkinci şart: yeni Auth dev'e çıkmış olmalı ve senin community container'ın güncel imajla çalışmalı. Dev'e süre ayarını ben eklemiyorum, ekip dev ortam değişkenine ekleyecek. Hazır olunca haber ver birlikte deneriz. Test hesabı kullan, dev'de silinen hesap anında anonimleşir ve geri gelmez

**5. `AccountStatusChanged` ne zaman, askıdaki hesabın içeriği gizlensin mi**
Olay henüz yok, planlı ama tarih yok. O zamana kadar durum değişince eski token'lar en fazla 90 saniyede ölüyor (60 sn token ömrü artı 30 sn saat farkı). İçeriği gizleme Community'nin ürün kararı, Auth'ta bunu zorunlu kılan bir şey yok. Olay gelmeden gizlemek istersen elinde güvenilir bir durum bilgisi olmaz, o yüzden olayı bekle

**6. JWKS önbellek süresi ve eski anahtarın yayın süresi**
Anahtar değişirken eski açık anahtarı JWKS'te bir süre daha yayınlayabiliyoruz (yeni özellik, dalda). Süre en az 90 saniye olmalı, ben pratikte 5 dakikadan fazla tutacağım. Sen de kendi önbelleğini en fazla 5 dakika yap ve tanımadığın `kid` görürsen bir kez yeniden çek. Game servisi böyle yapıyor, örnek alabilirsin

## SENDEN NE BEKLİYORUM

- Hangi servislerin hangi kapsamlarla token istediğini yaz (madde 1 ve 2)
- Dev testi için community imajını güncel tut
- Rapor saklama konusunda Alper'den dönüşü bekle
