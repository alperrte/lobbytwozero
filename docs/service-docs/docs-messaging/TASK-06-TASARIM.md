# LTZ-UMT-MSG-06 — Sesli Mesaj: Tasarım

**Servis:** `messaging-service` · **Branch:** `feature/messaging-service` (main'e merge yok) · **Gereksinim:** MSG-21
**Durum:** TASARIM — Umut onayladı (2026-11-10); uygulama planı `docs-messaging/TASK-06-PLAN.md`

## 1. Amaç

Kullanıcı sohbette ses kaydı alıp sesli mesaj olarak gönderir, alıcı kaydı dinler (MSG-21). PDA'da opsiyonel olan bu
özelliğin yapılmasına Umut karar verdi. Bu task yalnız backend'dir: kayıt, oynatıcı ve dalga formu çizimi frontend'in
işidir. Backend dosyayı kabul eder, doğrular, güvenli hale getirir, saklar ve yetkiliye sunar.

Sesli mesaj, MSG-04/05'te görsel için kurulan altyapının üzerine gelir: iki adımlı akış (önce yükle, sonra mesajla
gönder), `message_attachments` bağlantısı + `attachment_blobs` içeriği, bağlanmayan yüklemenin temizliği, iletmede
bayt kopyalamama, herkesten silmede içeriğin gerçekten silinmesi, görünürlük kuralı (`VisibleMessages`), SSE.

Ürün yayına çıkacak ve binlerce kullanıcıya açılacak (Umut). Ses dosyası kullanıcıdan gelen, tarayıcıda çalınan ikili
veridir; bu yüzden dosya olduğu gibi saklanmaz.

## 2. Kapsam

**Umut'un kararları (önceki konuşma, TASK-05 §7.8)**

- Yalnız **Opus** ses; kapsayıcı **WebM ya da Ogg**.
- Kayıt en fazla **2 dakika**, dosya en fazla **1 MB**.
- Sunucu dosyayı ayrıştırır, **yalnız ses paketlerini** alır ve **yeniden paketler**; dosyada gizlenmiş içerik
  (metadata, ek akış, sondaki baytlar vb.) kalmaz.

**Hedefler**

- Sesli mesaj yükleme (mevcut yükleme ucu, ses `Content-Type`'ı ile).
- Sesli mesaj gönderme (mevcut gönderim ucu, `attachmentId` ile); mesaj türü `VOICE`.
- Dinleme: yetki kontrollü indirme; tarayıcı oynatıcısının ileri/geri sarabilmesi için bayt aralığı (`Range`) desteği.
- Süre sunucuda paketlerden hesaplanır ve mesajla döner; istemcinin bildirdiği süreye güvenilmez.
- Dalga formu: istemcinin kayıt sırasında ürettiği 64 değerlik özet, doğrulanıp saklanır ve mesajla döner (dosyayı
  indirmeden çizilebilsin diye).
- Mevcut tüm mesaj işlemleri sesli mesajda da çalışır: yanıtla, ilet, tepki, benden sil, herkesten sil, okundu, SSE,
  sohbet listesi `lastMessage`.

**Hedef dışı**

- "Dinlendi" bilgisi (WhatsApp'taki mavi mikrofon). Okundu bilgisi (MSG-11) sesli mesajda da çalışır; ayrıca
  "dinlendi" ayrı bir tablo, uç ve SSE olayı gerektirir. PDA istemiyor; istenirse ayrı task.
- Sesli mesaja açıklama (body). WhatsApp'ta da yok.
- Sunucuda ses çözme/yeniden kodlama (libopus/ffmpeg gibi yerel bağımlılık), konuşmayı yazıya çevirme, gürültü azaltma.
- AAC/MP4 (eski Safari'nin kayıt biçimi), MP3, WAV ve diğer biçimler.
- Frontend (kayıt düğmesi, oynatıcı, dalga formu, hız seçimi).
- Nesne depolamaya geçiş (açık nokta, TASK-05 §7.7).

**Anti-hedefler** — uç noktalar çalışsa bile işi başarısız sayar:

- Saklanan dosyada istemcinin gönderdiği Opus paketleri **dışında** herhangi bir bayt kalıyorsa: etiketler
  (`OpusTags` yorumları, WebM `Tags`/`Attachments`/`Chapters`), ek izler/akışlar, `Void`/bilinmeyen öğeler, sayfa ya
  da öğe aralarındaki ve dosya sonundaki baytlar, Opus paketlerinin dolgu (padding) baytları.
- Süre ya da boyut sınırı istemcinin beyanına (başlıktaki süre, kapsayıcı zaman damgası, `Content-Length`) dayanıyorsa.
- Bozuk ya da kötü niyetli bir dosya ayrıştırıcıyı sınırsız bellek/CPU harcatabiliyor, istisna yığınıyla 500
  döndürebiliyor ya da dosya dışını okutabiliyorsa.
- Sesli mesajı göremeyen biri (başka sohbet, görünürlük sınırı altı, benden silmiş, herkesten silinmiş) dosyayı
  indirebiliyorsa ya da bayt aralığı isteği bu kuralı atlatıyorsa.
- Sesli mesaj iletmek dosya baytlarını kopyalıyorsa.
- Görsel akışında (MSG-17) davranış değişiyorsa (tür, sınır, hata kodu, yanıt alanları).

## 3. Kurallar

### 3.1 Kabul edilen dosya

- `Content-Type`: `audio/webm` ya da `audio/ogg`; parametreler (ör. `;codecs=opus`) yok sayılır. Biçim ilk baytlardan
  belirlenir (WebM: EBML `1A 45 DF A3`, Ogg: `OggS`) ve bildirilen türle aynı olmalıdır; aksi `422 INVALID_AUDIO`.
  Desteklenmeyen tür (ör. `audio/mp4`, `video/webm`) mevcut davranışla `415`.
- Gövde en çok 1 MB (mevcut yükleme gövde sınırı, `MessageAttachment.MAX_SIZE_BYTES`).
- Tam olarak **bir** Opus ses izi/akışı:
  - **Ogg:** tek mantıksal akış, ilk paketi geçerli `OpusHead`, ikincisi `OpusTags`. Birden çok akış (çoğullanmış
    ya da zincirlenmiş) reddedilir. Sayfalar art arda, geçerli CRC ile gelmelidir.
  - **WebM:** EBML başlığında `DocType` `webm`; tek iz, `CodecID = A_OPUS`, `CodecPrivate` geçerli `OpusHead`.
    MediaRecorder'ın yazdığı **boyutu bilinmeyen** `Segment` ve `Cluster` öğeleri desteklenmelidir.
- `OpusHead`: sürüm 1, kanal 1 ya da 2, kanal eşleme ailesi 0. Diğerleri `INVALID_AUDIO`.
- Her Opus paketi RFC 6716 §3'e göre yapısal olarak doğrulanır (TOC baytı, kod 0–3 çerçeve düzeni, çerçeve uzunlukları,
  paket başına ≤ 120 ms). Geçersiz paket `INVALID_AUDIO`.
- **Süre**, paketlerin TOC'undan hesaplanan örnek sayısının toplamından `pre-skip` çıkarılarak bulunur (48 kHz). Kapsayıcı
  zaman damgalarına ve başlık süresine bakılmaz. Süre **≥ 0,5 sn** ve **≤ 2 dk** olmalı; sınır aşımı
  `422 AUDIO_DURATION_OUT_OF_RANGE`. Kayıt düğmesi 2:00'de durduğunda kodlayıcının ürettiği birkaç milisaniyelik fazla
  reddedilmesin diye üst sınıra yapılandırılabilir **1 sn tolerans** eklenir (benim kararım).
- Ayrıştırıcı sınırları: öğe boyutu dosyanın kalanını aşamaz; iç içe derinlik ve paket sayısı üst sınırlıdır (2 dk /
  2,5 ms = 48.000 paket); bozuk/kesik dosya her durumda `422 INVALID_AUDIO` döner, ayrıntı istemciye ve loga yansımaz.
- WebM'de laced blok: ya doğru ayrıştırılır ya `INVALID_AUDIO`; asla yanlış okunmaz (seçim uygulayıcıda; tarayıcılar
  lacing kullanmaz).

### 3.2 Yeniden paketleme (Umut'un kararı)

Sunucu girdi dosyasından yalnız Opus paketlerini çıkarır ve **sıfırdan yeni bir dosya** yazar. Girdinin hiçbir baytı
ayrıştırılmadan çıktıya kopyalanmaz.

- **Çıktı biçimi her zaman WebM** (`audio/webm`), girdi Ogg olsa bile (benim kararım). Tek biçim sunucuda tek yazıcı,
  istemcide tek oynatma yolu demektir. WebM Opus güncel Chrome, Edge, Firefox ve Safari'de çalınır.
- `OpusHead` sıfırdan kurulur: sürüm 1, girdideki kanal sayısı ve `pre-skip`, giriş örnek hızı sabit 48000, çıkış
  kazancı 0, eşleme ailesi 0. `OpusTags` yazılmaz (WebM'de gerekmez).
- Paketlerdeki **dolgu baytları** (kod 3, padding bayrağı) atılır: paket aynı çerçevelerle, dolgusuz yeniden yazılır.
  Diğer paketler bayt bayt korunur (ses çözülmez).
- Zaman damgaları girdiden alınmaz; paket sürelerinden yeniden hesaplanır (tekdüze artan, boşluksuz).
- Çıktıda `Duration` dolu (MediaRecorder'ın WebM'inde yoktur; tarayıcılar süreyi "sonsuz" gösterir ve ileri saramaz).
  Kümeler 16 bitlik göreli zaman damgası sınırına sığacak sıklıkta açılır. Sarma için `Cues` eklenip eklenmeyeceği
  uygulayıcıya bırakılmıştır; koşul, Chrome/Firefox/Safari'de ileri sarmanın çalışmasıdır.
- Çıktı 1 MB'ı aşarsa (pratikte olmaz, kapsayıcı yükü küçüktür) `422 AUDIO_TOO_LARGE`.

**Sınır (dürüst not):** yeniden paketleme kapsayıcıdaki ve paket dolgusundaki gizli içeriği kaldırır; Opus çerçevelerinin
içindeki ses verisine gömülmüş bilgiyi (steganografi) kaldıramaz. Bunu ancak sunucuda çözüp yeniden kodlamak yapar;
bu yerel bağımlılık (libopus/ffmpeg) ister ve kapsam dışıdır. `SECURITY.md` §13 ve `NOTLAR.md`'ye yazılır.

### 3.3 Dalga formu

- İsteğe bağlı; yükleme isteğinin `waveform` sorgu parametresi: virgülle ayrılmış tam **64** tam sayı, her biri
  **0–100**. Biçim dışı her şey `400 INVALID_WAVEFORM`. Görsel yüklemede verilirse de `400`.
- Verilmezse `null` saklanır; istemci düz çubuk çizer.
- İstemcinin beyanıdır (sunucu sesi çözmediği için hesaplayamaz); yalnız çizim içindir, güvenlik kararına girmez.
  64 sayı serbest metin taşıyamaz.

### 3.4 Gönderme

- `POST .../messages {clientMessageId, attachmentId}`: tür, bağlanan dosyadan belirlenir (ses → `VOICE`,
  görsel → `IMAGE`). İstemci tür bildirmez.
- `VOICE` mesajında `body` verilemez → `400 VOICE_CAPTION_NOT_ALLOWED`. GIF ile birlikte verilemez (mevcut kural).
- Bağlama kuralları görselle aynı: aynı sohbete aynı kullanıcının yüklediği, gönderilmemiş, süresi dolmamış dosya;
  aksi `422 ATTACHMENT_NOT_AVAILABLE` ve mesaj/`seq`/outbox geri alınır.
- Yeniden deneme (`clientMessageId`) görseldeki gibi: aynı dosya kimliği ile tekrar → aynı mesaj.
- Yanıt (`replyToMessageId`) verilebilir; sesli mesaja yanıt verilebilir. Alıntı önizlemesinde `type = VOICE`,
  `snippet = null`.

### 3.5 Dinleme (indirme)

- Mevcut `GET .../attachments/{id}`; erişim kuralı aynen (mesajı görebilen aktif üye, gönderilmemişse yalnız yükleyen;
  aksi aynı `404`).
- **Bayt aralığı:** `Range: bytes=…` isteğine `206 Partial Content`, geçersiz aralığa `416`; yanıtta
  `Accept-Ranges: bytes`. Yetki denetimi aralık hesaplanmadan önce, her istekte yapılır. Safari'nin `<audio>` öğesi
  aralık desteği olmadan çalmaz. (Görselde de çalışması zararsızdır.)
- Başlıklar mevcut gibi: `no-store, private`, `nosniff`, `inline`, `default-src 'none'; sandbox`.

### 3.6 Diğer işlemler

- **İlet (MSG-20):** sesli mesaj iletilebilir; içerik paylaşılır, bayt kopyalanmaz; süre ve dalga formu bağlantıyla
  taşınır. "İletildi" etiketi aynen.
- **Herkesten sil (MSG-26):** bağlantı ve (başka bağlantı yoksa) içerik aynı transaction'da silinir; süre ve dalga
  formu da gider.
- **Benden sil, tepki, okundu, SSE `message` olayı, sohbet listesi `lastMessage`:** değişiklik yok; `attachment` alanı
  ses bilgisini taşır.
- **Oran ve bekleyen sınırları:** görselle **ortak** (kullanıcı başına 10 yükleme/dk, en çok 10 bekleyen, 1 saat
  TTL) — benim kararım; tek "yükleme" bütçesi.

## 4. API

Uç ve alan adlarında küçük sapmalar uygulayıcıya bırakılmıştır; davranış bırakılmamıştır.

1. **`POST /conversations/{id}/attachments`** — `consumes`'a `audio/webm`, `audio/ogg` eklenir; isteğe bağlı
   `?waveform=…`. `201` yanıtı aşağıdaki `AttachmentResponse`.
2. **`AttachmentResponse`** (yükleme yanıtı, `MessageResponse.attachment`):
   - mevcut: `id`, `mimeType`, `sizeBytes`, `url`;
   - `width`, `height`, `frameCount`: görselde dolu, seste `null` (şu an `int`; `Integer` olur — görsel istemcileri
     için JSON değişmez);
   - yeni: `durationMs` (seste dolu, görselde `null`), `waveform` (seste 64 sayı ya da `null`, görselde `null`).
   - Seste `mimeType` her zaman `audio/webm` (yeniden paketleme sonrası).
3. **`MessageType`**: `VOICE` eklenir.
4. **`GET .../attachments/{id}`**: `Range` desteği (§3.5).
5. **Yeni hata kodları:** `INVALID_AUDIO` (422), `AUDIO_DURATION_OUT_OF_RANGE` (422), `AUDIO_TOO_LARGE` (422),
   `INVALID_WAVEFORM` (400), `VOICE_CAPTION_NOT_ALLOWED` (400). Görsele özgü iletiler değişmez; genel iletiler
   (`MESSAGE_CONTENT_REQUIRED`, `TOO_MANY_UPLOADS`, `TOO_MANY_PENDING_ATTACHMENTS`, `ATTACHMENT_NOT_AVAILABLE`) sesi
   de kapsayacak biçimde genelleştirilir.
6. Outbox `MessageSent` içerik taşımaz; `type` alanı varsa `VOICE` değerini alabilir (notification-service sözleşmesine
   not).

## 5. Veri

Migration **`V136`** (MESSAGING aralığı V131–V140).

- `messages.type` CHECK'ine `VOICE`. `VOICE` mesajında `body` boş olmalı (DB CHECK).
- `message_attachments`:
  - `mime_type` CHECK'ine `audio/webm`.
  - `width`, `height`, `frame_count` nullable olur; yeni `duration_ms int`, `waveform bytea`.
  - Tür tutarlılığı CHECK'i: görselde ölçüler dolu (mevcut aralıklar), `duration_ms` ve `waveform` boş; seste ölçüler
    boş, `duration_ms` 500–121000, `waveform` boş ya da `octet_length = 64`.
- `attachment_blobs`: değişmez (1 MB CHECK aynen).
- Mevcut satırlar görseldir ve yeni CHECK'i sağlar; veri taşıması yok.

## 6. Güvenlik

- Dosya kullanıcı girdisidir ve başka kullanıcının tarayıcısında çalınır: biçim içerikten belirlenir, yapısı
  sınırlarla ayrıştırılır, yalnız doğrulanmış Opus paketleri yeni bir kapsayıcıya yazılır (§3.2).
- Ayrıştırıcı saf Java, yeni bağımlılık yok; doğrusal zaman, girdi boyutuyla sınırlı bellek. Görseldeki eşzamanlılık
  sınırı (`MEDIA_BUSY`) sese de uygulanır mı uygulayıcıya bırakılmıştır; koşul, 1 MB'lık kötü niyetli dosyanın
  işleminin milisaniyeler mertebesinde kalması ve bunun testle gösterilmesidir.
- Dalga formu yalnız sayı; içerik ve dalga formu loglanmaz.
- Erişim, bayt aralığı dahil, her istekte mesaj görünürlüğüyle denetlenir; var/yok sızıntısı yok (aynı `404`).
- Steganografi sınırı (§3.2) belgelenir.

## 7. Test ve teslim

- Birim: Ogg ve WebM ayrıştırıcı (geçerli MediaRecorder çıktısı biçimleri, boyutu bilinmeyen öğeler, bozuk CRC, kesik
  dosya, çok akış/çok iz, Opus olmayan codec, eşleme ailesi ≠ 0, aşırı büyük öğe boyutu, derin iç içelik), Opus paket
  doğrulama ve dolgu atma (kod 0–3), süre hesabı (pre-skip dahil), WebM yazıcı (çıktı yeniden ayrıştırılınca aynı
  paketler ve süre). Gizli içerik testi: girdiye etiket, `Void`, ek iz/akış ve sona işaret baytları eklenir; çıktıda
  işaret hiçbir yerde geçmez.
- Test sesleri kod içinde üretilir (geçerli TOC'lu sentetik Opus paketleri); repo'ya ikili test dosyası eklenmesi
  gerekmez. Gerçek bir MediaRecorder kaydıyla (Chrome WebM, Firefox Ogg) tek seferlik elle doğrulama teslim raporuna
  yazılır.
- Entegrasyon (Testcontainers PostgreSQL, mevcut desen): yükle → gönder → geçmiş/liste/SSE'de `VOICE` + süre + dalga
  formu; açıklamalı sesli mesaj `400`; görsel ile ses türü karışmaz; bayt aralığı `206`/`416` ve yetkisiz aralık
  isteği `404`; iletmede bayt kopyalanmaz; herkesten silmede içerik gider; sınır dışı süre/boyut; dalga formu hataları;
  V136 şema testi (CHECK'ler).
- Görsel testlerinin tamamı değişmeden geçmeli.
- `pre-push messaging-service` geçmeli; commit öncesi `pre-push full dev` ve SonarQube (Umut'un akışı).
- Belgeler: `docs/api.md` MESSAGING kayıtları, `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs/SECURITY.md`
  §13, `docs-security/messaging-service/NOTLAR.md`, `docs/folder-structure.md` (gerekirse), `docs-messaging/TASK-06.md`.
- Frontend notları `TASK-06.md` açık noktalarına: `MediaRecorder` için `audio/webm;codecs=opus` (Firefox'ta
  `audio/ogg;codecs=opus` de olur) ve **`audioBitsPerSecond` ≈ 32000** (varsayılan bit hızında 2 dk 1 MB'ı aşar);
  2:00'de kaydı durdurma; dalga formunu kayıt sırasında 64 değere indirme; eski Safari (yalnız AAC kaydeden) için
  kayıt düğmesini gizleme; oynatıcının klavye ve ekran okuyucu erişimi (`web-rules.md`).

## 8. Uygulayıcıya bırakılanlar

- Ayrıştırıcı/yazıcı sınıflarının bölünüşü ve adları (`infrastructure/storage` altında, `ImageInspectorPort` benzeri bir
  port ardında).
- WebM lacing desteği ya da reddi (§3.1), `Cues` (§3.2), ses için eşzamanlılık sınırı (§6).
- Gönderimde dosya türünün belirlenme yeri (bağlama transaction'ında ön okuma ya da eşdeğeri; yarış güvenliği koşuluyla).
- Range desteğinin uygulanışı (Spring `Resource` yanıtı ya da eşdeğeri).

## 9. Benim verdiğim kararlar (Umut'un kontrolü için)

1. Çıktı her zaman WebM; Ogg girdi WebM'e paketlenir.
2. Süre alt sınırı 0,5 sn; üst sınıra 1 sn tolerans (2:01'e kadar kabul).
3. Dalga formu istemciden, isteğe bağlı, 64 değer × 0–100.
4. Sesli mesaja açıklama yok.
5. "Dinlendi" bilgisi bu task'ta yok.
6. Yükleme oranı ve bekleyen sınırı görselle ortak.
7. Tür istemciden alınmaz, dosyadan belirlenir; aynı `attachmentId` alanı kullanılır.
8. Steganografi sınırı kabul edilir ve belgelenir (sunucuda yeniden kodlama yok).
