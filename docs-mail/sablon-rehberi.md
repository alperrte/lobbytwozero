# Mail Service — Şablon Rehberi

Güncelleme: 2026-11-09. Sorumlu: Kerem. Bu belge **Mail Service'in kendi içindeki e-posta şablonlarını** anlatır (şablon ekleyen/değiştiren Mail geliştiricisi ve ajanlar için). Başka bir servisten mail attırmak için bkz. `entegrasyon-rehberi.md` (platform kapısı; bu belgeden ayrıdır).

## Mantık

Şablon **dosyadır**; veritabanı ve migration gerekmez. Üç parça var ve her biri tek yerde durur:

| Parça | Nerede | Ne yapar |
| --- | --- | --- |
| **Kabuk** | `src/main/resources/mail/layout.html` | Logo başlığı, kart, altbilgi çerçevesi, imza çizgisi, açık tema iyileştirmesi |
| **Bileşenler** | `application/service/MailBlocks.java` | Etiket, başlık, paragraf, kod kutusu, düğme, yedek bağlantı, tarih kutusu, uyarı kutusu, özellik satırı (HTML burada) |
| **İçerik** | `src/main/resources/mail/templates/<TÜR>.<dil>.mail` | Konu, renk, etiket, başlık ve bloklar; **yalnız düz metin** |

Altbilgi (marka, tagline, mailin nedeni, yasal not, telif) `TemplateRenderer` içindedir ve her dilde otomatik eklenir. Düz metin (text/plain) sürümü **bloklardan otomatik üretilir**; ayrıca yazılmaz. Görünümü değiştirmek için `MailBlocks` ya da `layout.html` düzenlenir, 30 şablona dokunulmaz.

**İstisna: `NOTICE`.** Genel bilgilendirme (platform kapısı) için şablon dosyası yoktur: başlık, mesaj ve düğme çağıran servisten gelir, görünüm yine `MailBlocks` bileşenlerinden ve kabuktan kurulur (`TemplateRenderer.renderNotice`). Katalog dinamik türleri (`MailType.isDynamic()`) atlar.

## Dosya biçimi

Satır tabanlı, `anahtar: değer`. Boş satırlar ve `#` ile başlayan satırlar yok sayılır. Değerler **düz metindir** (HTML yazılmaz; kaçışlama otomatiktir).

```
# EMAIL_VERIFICATION (tr)
subject: LTZ e-posta doğrulama kodun
kind: accent
label: E-POSTA DOĞRULAMA
title: E-posta adresini doğrula
note-kind: accent
text: LTZ hesabını doğrulamak için aşağıdaki kodu gir. Kod {{validityMinutes}} dakika geçerli.
code: DOĞRULAMA KODU
note: Bu isteği sen yapmadıysan bu e-postayı yok sayabilirsin. Kodu kimseyle paylaşma.
```

**Başlık anahtarları** (ilk dördü zorunlu):

| Anahtar | Anlam |
| --- | --- |
| `subject` | Konu satırı (`{{değişken}}` içerebilir; iki dilli mailde "TR / EN" birleşir) |
| `kind` | Kategori rengi: `accent` (pembe), `warn` (turuncu), `danger` (kırmızı), `success` (yeşil) |
| `label` | Başlık üstündeki küçük etiket (BÜYÜK HARF önerilir) |
| `title` | Ana başlık |
| `note-kind` | Uyarı kutusunun rengi (isteğe bağlı, varsayılan `warn`) |

**İçerik blokları** (dosyadaki sırayla görünür; aynı anahtar birden çok kez kullanılabilir):

| Anahtar | Değer | Gerektirdiği değişken |
| --- | --- | --- |
| `text` | Paragraf (her `text:` satırı ayrı paragraf olur) | — |
| `code` | Kod kutusunun etiketi (örn. `DOĞRULAMA KODU`); kodun kendisi `{{code}}` | `code` |
| `button` | Düğme yazısı; adres `{{link}}` | `link` |
| `link-fallback` | "Düğme çalışmazsa…" cümlesi; adres altına eklenir | `link` |
| `date` | Tarih kutusunun etiketi (örn. `Tarih:`); değer `{{occurredAt}}` | `occurredAt` |
| `note` | Uyarı/bilgi kutusu | — |
| `feature` | `BAŞLIK \| açıklama`; art arda gelenler tek liste olur | — |

**Kullanılabilen değişkenler** (metinde `{{ad}}` olarak): `code`, `link`, `validityMinutes`, `occurredAt`. Başka değişken **kullanılamaz**; açılışta reddedilir.

## Yeni mail türü eklemek

1. `MailType` enum'una değer ekle (adı dosya adıdır). Yük gerekiyorsa uç/`MailRequestMapper` tarafı ayrıca düşünülür.
2. `src/main/resources/mail/templates/<TÜR>.tr.mail` ve `<TÜR>.en.mail` oluştur.
3. `mail_messages.type` CHECK kısıtına yeni türü ekleyen yeni bir Flyway migration yaz (sonraki numara, aralık V81–V90; `V85__add_welcome_mail_type.sql` örnektir).
4. `mvn -pl services/mail-service test`: katalog testi her tür × dil dosyasını, değişken uyumunu ve blok-yük uyumunu denetler.

Mevcut bir şablonu değiştirmek için yalnız ilgili `.mail` dosyasını düzenle; migration gerekmez.

## Güvence

- **Açılış denetimi:** Eksik, hatalı ya da bilinmeyen anahtar/değişken içeren dosya uygulamanın açılmasını engeller (`MailTemplateCatalog`, fail-fast); canlıda şablon hatası görmezsin.
- **Kaçışlama:** Tüm değerler HTML-kaçışlanır; dışarıdan gelen kod ve bağlantı biçimlendirmeyi bozamaz. Konu satırından satır sonları temizlenir.
- **Önizleme:** Gerçek görünümü Mailpit'te (`http://127.0.0.1:8025`) görmek için servisi çalıştırıp ilgili akışı tetikle; Compose'ta `docker compose up -d --build mail-service`.
