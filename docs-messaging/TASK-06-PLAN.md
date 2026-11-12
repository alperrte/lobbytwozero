# LTZ-UMT-MSG-06 Uygulama Planı — Sesli Mesaj

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** messaging-service'e sesli mesaj (MSG-21) eklemek: Opus (WebM/Ogg) yükleme, sunucuda yalnız ses paketleriyle WebM'e yeniden paketleme, `VOICE` mesajı, bayt aralıklı dinleme.

**Architecture:** Saf Java ayrıştırıcılar (`OggOpusReader`, `WebmOpusReader`) girdiden yalnız `OpusHead` değerlerini ve Opus paketlerini çıkarır; `OpusPacket` her paketi RFC 6716'ya göre doğrular, süresini hesaplar ve dolgusunu atar; `WebmOpusWriter` sıfırdan WebM yazar. Bunlar `VoiceInspectorPort` ardındadır (görseldeki `ImageInspectorPort` gibi). Ses, görselin yükleme/bağlama/iletme/silme altyapısını (`message_attachments` + `attachment_blobs`) kullanır; yeni tablo yoktur.

**Tech Stack:** Java 25, Spring Boot (Web MVC, Data JPA, Security), PostgreSQL 17, Flyway, Testcontainers, MockMvc.

**Spec:** `docs-messaging/TASK-06-TASARIM.md`

## Global Constraints

- Çalışma dizini: `backend/services/messaging-service`. Test komutu: `.\mvnw.cmd test -Dtest=<Sınıf>` (Docker açık olmalı).
- **Commit yok.** Claude commit/push/add yapmaz; Umut atar. Adımlarda "Commit" yerine testleri koşmak vardır.
- Migration: yalnız `V136__voice_messages.sql`. Önceki migration'lar değiştirilmez.
- **Yeni bağımlılık yok.** Ses çözülmez/yeniden kodlanmaz.
- Kabul: `audio/webm`, `audio/ogg` (parametreler yok sayılır); çıktı her zaman `audio/webm`.
- Dosya ≤ 1 MB (`MessageAttachment.MAX_SIZE_BYTES`, girdi ve çıktı). Süre 500 ms ≤ d ≤ 120 000 ms + 1000 ms tolerans (DB CHECK 500–121000).
- Süre = Σ paket örnekleri (48 kHz) − `pre-skip`; kapsayıcı zaman damgası/süresi kullanılmaz.
- `OpusHead`: sürüm 1 (üst 4 bit 0), kanal 1–2, eşleme ailesi 0. Çıktı başlığı: sürüm 1, kanal, pre-skip, giriş hızı 48000, kazanç 0, aile 0.
- Dalga formu: tam 64 tam sayı, her biri 0–100, `waveform` sorgu parametresi, virgülle ayrılmış; DB'de 64 baytlık `bytea`.
- Hata kodları: `INVALID_AUDIO` 422, `AUDIO_DURATION_OUT_OF_RANGE` 422, `AUDIO_TOO_LARGE` 422, `INVALID_WAVEFORM` 400, `VOICE_CAPTION_NOT_ALLOWED` 400.
- Yükleme oranı/bekleyen sınırı görselle ortak (`ltz.messaging.attachments.*`).
- Görsel akışında davranış değişmez; mevcut tüm testler değişmeden geçer (yalnız genelleştirilen hata iletileri hariç).
- İçerik, dalga formu ve bayt loglanmaz; ayrıştırma hatasının ayrıntısı istemciye/loga yansımaz.
- Test adları Türkçe camelCase cümle; ikili test dosyası eklenmez, test sesleri kodda üretilir (`TestAudio`).
- Kalite: `pre-push messaging-service` geçmeli (JaCoCo kapsam ve değişen satır, Checkstyle/PMD yeni ihlal 0).

## Review Focus

1. **Chrome MediaRecorder WebM'i** — boyutu bilinmeyen `Segment` ve `Cluster`, `Duration` yok, `SimpleBlock`; kabul edilmeli ve çıktıda süre dolu olmalı. (Task 3: `boyutuBilinmeyenSegmentVeKumelerOkunur`)
2. **Firefox Ogg'u** — `OpusTags` iki sayfaya bölünmüş, son sayfa EOS; kabul edilmeli. (Task 2: `birdenCokSayfayaBolunmusPaketBirlesir`)
3. **Kötü niyetli öğe boyutu** — dosyanın kalanından büyük boyut, 8 baytlık vint, derin iç içelik → `INVALID_AUDIO`, istisna sızmaz, hızlı biter. (Task 3: `dosyaninKalanindanBuyukOgeReddedilir`, Task 4: `birMegabaytlikKotuNiyetliDosyaHizliReddedilir`)
4. **Sesli mesajın yeniden denemesi** — aynı `clientMessageId` + aynı `attachmentId` → aynı mesaj; `CLIENT_MESSAGE_ID_REUSED` değil. (Task 6: `sesliMesajYenidenDenenirseAyniMesajDoner`)
5. **Safari'nin aralık isteği** — `Range: bytes=0-1` → `206`; yetkisiz kullanıcının aralık isteği → `404`. (Task 7)

---

### Task 1: Opus paketi ve başlığı

**Files:**
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/OpusPacket.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/OpusHead.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/OpusStream.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/MalformedAudioException.java`
- Create (test): `src/test/java/com/ltz/messaging/TestAudio.java` (yalnız paket/başlık üreticileri bu task'ta)
- Test: `src/test/java/com/ltz/messaging/infrastructure/storage/audio/OpusPacketTest.java`, `OpusHeadTest.java`

**Interfaces:**
- Produces:
  - `final class MalformedAudioException extends Exception` (paket içi; yığın izi üretmez: `super(null, null, false, false)`).
  - `record OpusPacket(byte[] bytes, int samples)`; `static OpusPacket parse(byte[] raw) throws MalformedAudioException` — RFC 6716 §3.1–3.2 kuralları (R1–R7): boş paket, çerçeve uzunluğu > 1275, kod 1'de tek uzunluk, kod 2/3 uzunluk taşması, kod 3'te M = 0 ya da toplam > 120 ms (5760 örnek), dolgu uzunluğu taşması → istisna. `bytes`: kod 3 + dolgu bayrağı varsa dolgu baytları ve dolgu uzunluk alanı atılmış, P bayrağı temizlenmiş paket; diğer durumlarda girdinin kopyası. `samples`: çerçeve sayısı × çerçeve süresi (48 kHz; config 0–11 SILK 10/20/40/60 ms, 12–15 hibrit 10/20, 16–31 CELT 2,5/5/10/20).
  - `record OpusHead(int channels, int preSkip)`; `static OpusHead parse(byte[] packet) throws MalformedAudioException` ("OpusHead" imzası, ≥ 19 bayt, sürüm üst 4 bit 0, kanal 1–2, aile 0); `byte[] toBytes()` → 19 bayt, sürüm 1, giriş hızı 48000, kazanç 0, aile 0.
  - `record OpusStream(OpusHead head, List<OpusPacket> packets)`; `long totalSamples()`; `long durationMillis()` = `max(0, totalSamples − preSkip) / 48`.
  - `TestAudio.packet(int config, boolean stereo, int frameBytes)` (kod 0), `TestAudio.paddedPacket(int config, int frames, int frameBytes, byte[] padding)` (kod 3 CBR + dolgu), `TestAudio.head(int channels, int preSkip)` (geçerli OpusHead baytları), `TestAudio.packets(int count)` (config 31 = CELT FB 20 ms, mono, kod 0, 40 baytlık rastgele olmayan çerçeve).

- [ ] **Step 1: Testleri yaz**
  - `kod0PaketSuresiConfigdenHesaplanir`: config 3 (SILK 60 ms) → 2880; config 16 (CELT 2,5 ms) → 120; config 15 (hibrit 20 ms) → 960.
  - `kod1IkiEsitCerceveSayar`: `(N−1)` çift olan kod 1 → 2 × süre; `(N−1)` tek → istisna.
  - `kod2UzunlukTasarsaReddedilir`: ilk çerçeve uzunluğu kalan bayttan büyük → istisna; 2 baytlık uzunluk kodlaması (≥ 252) doğru okunur.
  - `kod3DolgusuAtilirCerceveleriKorunur`: `paddedPacket(31, 3, 10, "GIZLI".getBytes())` → `bytes` içinde `GIZLI` yok, P bayrağı 0, uzunluk = 2 + 30, `samples` = 3 × 960.
  - `kod3SifirCerceveYaDa120msUstuReddedilir`: M = 0 → istisna; config 3 (60 ms) M = 3 → istisna.
  - `bosPaketVe1275UstuCerceveReddedilir`.
  - `opusHeadDogrulanirVeYenidenKurulur`: `head(2, 312)` → `channels 2, preSkip 312`; `toBytes()` ile parse tekrar aynı; giriş hızı alanı 48000, kazanç 0.
  - `opusHeadKanalSurumVeAileKurallari`: kanal 0/3, sürüm baytı 0x10, aile 1, imza hatalı → istisna.
- [ ] **Step 2: Koş, derlenmediğini/kaldığını gör** — `.\mvnw.cmd test -Dtest=OpusPacketTest,OpusHeadTest` → FAIL.
- [ ] **Step 3: Sınıfları yaz** (yukarıdaki imzalar).
- [ ] **Step 4: Koş** — aynı komut → PASS.

---

### Task 2: Ogg okuyucu

**Files:**
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/OggOpusReader.java`
- Modify (test): `TestAudio.java` — Ogg yazıcı (test tarafı, üretimden bağımsız)
- Test: `src/test/java/com/ltz/messaging/infrastructure/storage/audio/OggOpusReaderTest.java`

**Interfaces:**
- Consumes: Task 1 `OpusPacket.parse`, `OpusHead.parse`, `OpusStream`, `MalformedAudioException`.
- Produces: `static OpusStream OggOpusReader.read(byte[] file, int maxPackets) throws MalformedAudioException`.
  Kurallar: art arda sayfalar (`OggS`, sürüm 0, CRC-32 poly `0x04C11DB7`, başlangıç 0, yansıtmasız, CRC alanı sıfırlanarak); tek seri numarası; ilk sayfa BOS ve yalnız `OpusHead`; sayfa sıra numarası 1'er artar; paketler sayfalar arası devam eder (lacing 255); 2. paket `OpusTags` imzası taşımalı ve **atılır**; ilk sayfadan sonra BOS ya da farklı seri → istisna; son paket tamamlanmamışsa, sayfa kesikse ya da son sayfadan sonra bayt varsa → istisna; paket sayısı `maxPackets`'ı aşarsa → istisna; en az 1 ses paketi.
  - `TestAudio.ogg(byte[] head, byte[] tags, List<byte[]> packets)` ve seçenekli sürüm `TestAudio.ogg(OggOptions)`: `extraStream`, `trailing`, `corruptCrc`, `splitTagsAcrossPages`, `maxPacketsPerPage`.

- [ ] **Step 1: Testleri yaz**
  - `gecerliOggPaketleriVeBasligiOkunur`: 50 × 20 ms paket, `head(1, 312)` → 50 paket, `durationMillis` = (50×960−312)/48.
  - `birdenCokSayfayaBolunmusPaketBirlesir`: `OpusTags` 2 sayfaya bölünmüş + 300 baytlık ses paketi lacing 255+45 → doğru okunur; son sayfa EOS.
  - `etiketlerAtilir`: `tags` içinde `GIZLI-ETIKET` → dönen paketlerin hiçbirinde bu bayt dizisi yok.
  - `bozukCrcReddedilir`, `kesikSayfaReddedilir`, `sondaFazlaBaytReddedilir`.
  - `ikinciAkisReddedilir`: farklı seri numaralı ikinci BOS sayfası → istisna.
  - `opusOlmayanAkisReddedilir`: ilk paket `OpusHead` değil (ör. `\x01vorbis`) → istisna.
  - `paketSiniriAsilirsaReddedilir`: `maxPackets` = 10, 11 paket → istisna.
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=OggOpusReaderTest` → FAIL.
- [ ] **Step 3: `OggOpusReader` yaz.**
- [ ] **Step 4: Koş** → PASS.

---

### Task 3: WebM okuyucu

**Files:**
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/WebmOpusReader.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/Ebml.java` (öğe kimlikleri sabitleri + vint okuma/yazma; Task 4 yazıcısı da kullanır)
- Modify (test): `TestAudio.java` — WebM yazıcı (test tarafı, kendi küçük EBML kodlayıcısıyla)
- Test: `src/test/java/com/ltz/messaging/infrastructure/storage/audio/WebmOpusReaderTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `Ebml`: kimlikler `EBML 0x1A45DFA3, DOC_TYPE 0x4282, SEGMENT 0x18538067, INFO 0x1549A966, TRACKS 0x1654AE6B, TRACK_ENTRY 0xAE, TRACK_NUMBER 0xD7, TRACK_TYPE 0x83, CODEC_ID 0x86, CODEC_PRIVATE 0x63A2, CLUSTER 0x1F43B675, TIMESTAMP 0xE7, SIMPLE_BLOCK 0xA3, BLOCK_GROUP 0xA0, BLOCK 0xA1, CUES 0x1C53BB6B`; yazıcı için ek olarak `TIMESTAMP_SCALE 0x2AD7B1, DURATION 0x4489, MUXING_APP 0x4D80, WRITING_APP 0x5741, TRACK_UID 0x73C5, CODEC_DELAY 0x56AA, SEEK_PRE_ROLL 0x56BB, AUDIO 0xE1, SAMPLING_FREQUENCY 0xB5, CHANNELS 0x9F, CUE_POINT 0xBB, CUE_TIME 0xB3, CUE_TRACK_POSITIONS 0xB7, CUE_TRACK 0xF7, CUE_CLUSTER_POSITION 0xF1`, EBML başlık alanları `0x4286, 0x42F7, 0x42F2, 0x42F3, 0x4287, 0x4285`.
  - `static OpusStream WebmOpusReader.read(byte[] file, int maxPackets) throws MalformedAudioException`.
  Kurallar: 0. baytta EBML başlığı, `DocType` = `webm`; ardından tek `Segment`; boyutu bilinmeyen (tüm veri bitleri 1) yalnız `Segment` ve `Cluster` için kabul — `Cluster` bir sonraki `Segment` düzeyi kimlikte ya da dosya sonunda biter; diğer her öğenin boyutu ebeveyninin kalanını aşamaz; iç içe derinlik ≤ 8; `Tracks` ilk `Cluster`'dan önce, tam 1 `TrackEntry`, `TrackType` 2, `CodecID` `A_OPUS`, `CodecPrivate` → `OpusHead.parse`; bloklar (`SimpleBlock`, `BlockGroup/Block`) iz numarası = o iz, lacing bitleri (`flags & 0x06`) ≠ 0 → istisna (benim kararım: reddet); blok sırası dosya sırası, zaman damgaları yok sayılır; tanınmayan/atılan öğeler (`Tags`, `Void`, `SeekHead`, `Cues`, `Chapters`, `Attachments`, `Info` içeriği) atlanır; bilinen boyutlu `Segment`'ten sonra bayt varsa → istisna; en az 1 paket; `maxPackets` sınırı.
  - `TestAudio.webm(WebmOptions)`: `unknownSizes`, `tags`, `voidElement`, `extraVideoTrack`, `codecId`, `laced`, `trailing`, `blockGroups`.

- [ ] **Step 1: Testleri yaz**
  - `gecerliWebmPaketleriVeBasligiOkunur`: bilinen boyutlu, 100 paket → 100 paket, `head` değerleri.
  - `boyutuBilinmeyenSegmentVeKumelerOkunur`: Chrome biçimi (unknown-size Segment + 3 unknown-size Cluster, Duration yok) → tüm paketler sırayla.
  - `blockGroupIcindekiBlokOkunur`.
  - `etiketVoidVeCuesAtlanir`: `Tags` içinde `GIZLI-ETIKET`, `Void` içinde `GIZLI-VOID` → okunur, paketlerde işaretler yok.
  - `ikinciIzReddedilir`, `opusOlmayanCodecReddedilir` (`A_VORBIS`), `matroskaDocTypeReddedilir`, `laceliBlokReddedilir`.
  - `dosyaninKalanindanBuyukOgeReddedilir`: `Tracks` boyutu 2^40 → istisna (OOM ya da `ArrayIndexOutOfBounds` değil).
  - `derinIcIceligeSinirVardir`: 9 seviye iç içe bilinmeyen öğe → istisna.
  - `kesikDosyaReddedilir`: geçerli dosyanın son 7 baytı kesik → istisna. `sondaFazlaBaytReddedilir` (bilinen boyutlu segment).
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=WebmOpusReaderTest` → FAIL.
- [ ] **Step 3: `Ebml` ve `WebmOpusReader` yaz.**
- [ ] **Step 4: Koş** → PASS.

---

### Task 4: WebM yazıcı ve `VoiceInspectorPort`

**Files:**
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/audio/WebmOpusWriter.java`
- Create: `src/main/java/com/ltz/messaging/application/port/VoiceInspectorPort.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/storage/OpusVoiceInspector.java`
- Create: `src/main/java/com/ltz/messaging/infrastructure/config/VoiceProperties.java`
- Modify: `infrastructure/config/MessagingApplicationConfig.java` (properties kaydı), `src/main/resources/application.properties`
- Modify: `domain/exception/MessagingErrorCode.java` (5 yeni kod — Global Constraints)
- Test: `.../storage/audio/WebmOpusWriterTest.java`, `.../storage/OpusVoiceInspectorTest.java`, `.../config/VoicePropertiesTest.java`

**Interfaces:**
- Consumes: Task 1–3.
- Produces:
  - `static byte[] WebmOpusWriter.write(OpusStream stream)`: EBML başlığı (EBMLVersion 1, ReadVersion 1, MaxIDLength 4, MaxSizeLength 8, DocType `webm`, DocTypeVersion 4, DocTypeReadVersion 2); bilinen boyutlu `Segment`: `Info` (TimestampScale 1 000 000, Duration float64 ms = `durationMillis`, MuxingApp/WritingApp `ltz-messaging`), `Tracks` (TrackNumber 1, TrackUID 1, TrackType 2, CodecID `A_OPUS`, CodecPrivate `head.toBytes()`, CodecDelay = preSkip × 1e9 / 48000 ns, SeekPreRoll 80 000 000, Audio{SamplingFrequency 48000.0, Channels}), `Cues` (küme başına CuePoint; CueClusterPosition sabit 8 baytlık uint, Segment veri başlangıcına göre) **kümelerden önce**, `Cluster`'lar (her biri ≤ 5000 ms; Timestamp mutlak ms; SimpleBlock: iz vint `0x81`, int16 göreli ms, flags `0x80`, `packet.bytes()`). Zaman damgası = önceki paketlerin örnek toplamı / 48 (tam sayı ms).
  - `interface VoiceInspectorPort { ProcessedVoice process(byte[] content, String declaredContentType); record ProcessedVoice(String mimeType, byte[] content, int durationMillis) }` (record `equals/hashCode/toString` `ProcessedImage` gibi dizi içeriğine göre; `toString` baytları yazmaz).
  - `@ConfigurationProperties("ltz.messaging.voice") record VoiceProperties(Duration minDuration, Duration maxDuration, Duration durationTolerance)`; kurucu: `minDuration ≥ 500 ms` ve `maxDuration + durationTolerance ≤ 121 s` değilse `IllegalArgumentException` (DB CHECK ile uyum). `application.properties`: `ltz.messaging.voice.min-duration=PT0.5S`, `max-duration=PT2M`, `duration-tolerance=PT1S`.
  - `OpusVoiceInspector implements VoiceInspectorPort` (`@Component`): boş → `INVALID_AUDIO`; > 1 MB → `AUDIO_TOO_LARGE`; ilk baytlardan biçim (`OggS` → `audio/ogg`, `1A45DFA3` → `audio/webm`) normalleştirilmiş `Content-Type` ile aynı değilse `INVALID_AUDIO`; `maxPackets` = (maxDuration + tolerance) / 2,5 ms; okuyucu → süre denetimi (`AUDIO_DURATION_OUT_OF_RANGE`) → `WebmOpusWriter.write` → çıktı > 1 MB ise `AUDIO_TOO_LARGE` → `ProcessedVoice("audio/webm", bytes, durationMillis)`. `MalformedAudioException` → `INVALID_AUDIO` (kök neden bağlanmaz, loglanmaz). Eşzamanlılık semaforu yok (benim kararım; hız testi gerekçesi).

- [ ] **Step 1: Testleri yaz**
  - `WebmOpusWriterTest.yazilanDosyaYenidenOkununcaAyniPaketlerVeSure`: 6000 × 20 ms paket (2 dk) → `WebmOpusReader.read(write(s))` aynı paket baytları, aynı `head`; Info/Duration = 120000 − preSkip/48 ms; kümeler 16 bit sınırına sığar (her kümenin göreli zaman damgası ≤ 5000).
  - `WebmOpusWriterTest.cuesKumeKonumlariniGosterir`: her CuePoint'in CueClusterPosition'ı, Segment veri başlangıcından o ofsette bir `Cluster` kimliğine denk gelir.
  - `OpusVoiceInspectorTest.oggGirdiWebmCiktiyaPaketlenir`: Ogg (1 kanal, 30 sn) → `mimeType audio/webm`, `durationMillis` doğru, çıktı `1A45DFA3` ile başlar.
  - `OpusVoiceInspectorTest.gizliIcerikCiktidaKalmaz`: işaret `LTZ-GIZLI-ISARET`; (a) WebM girdide `Tags`, `Void`, `SeekHead` ve kod 3 paket dolgusunda, (b) Ogg girdide `OpusTags` yorumunda ve kod 3 dolgusunda → çıktının hiçbir ofsetinde yok. Ayrıca çıktı başlığında kazanç 0, giriş hızı 48000 (girdi kazancı 6 dB, hızı 44100 iken).
  - `OpusVoiceInspectorTest.bildirilenTurIcerikleUyusmazsaReddedilir`: Ogg içerik + `audio/webm` → `INVALID_AUDIO`; `audio/ogg; codecs=opus` kabul.
  - `OpusVoiceInspectorTest.sureSinirlari`: 0,4 sn → `AUDIO_DURATION_OUT_OF_RANGE`; 0,5 sn kabul; 121 sn kabul; 121,02 sn → `AUDIO_DURATION_OUT_OF_RANGE`.
  - `OpusVoiceInspectorTest.birMegabaytUstuReddedilir`: 1 048 577 bayt → `AUDIO_TOO_LARGE`.
  - `OpusVoiceInspectorTest.birMegabaytlikKotuNiyetliDosyaHizliReddedilir`: (a) 1 MB'lık tek baytlı paketlerden oluşan Ogg, (b) 1 MB'lık iç içe `Void` dolu WebM → her biri 500 ms altında (`assertTimeoutPreemptively`) `INVALID_AUDIO` ya da `AUDIO_DURATION_OUT_OF_RANGE`.
  - `VoicePropertiesTest`: `max PT2M + tolerans PT2S` → istisna; `min PT0.1S` → istisna.
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=WebmOpusWriterTest,OpusVoiceInspectorTest,VoicePropertiesTest` → FAIL.
- [ ] **Step 3: Sınıfları yaz; hata kodlarını ekle.**
- [ ] **Step 4: Koş** → PASS; ayrıca `.\mvnw.cmd test -Dtest=ServiceBoundaryTest` (port/altyapı katman kuralları) → PASS.
- [ ] **Step 5: Elle doğrulama (rapor için):** Chrome ve Firefox `MediaRecorder` ile birer kayıt (`audioBitsPerSecond: 32000`) alınıp `OpusVoiceInspector`'dan geçirilir; çıktı Chrome/Firefox'ta çalınır ve ileri sarılır. Yapılamazsa `TASK-06.md`'de NOT VERIFIED.

---

### Task 5: Şema V136, ses yükleme ve dalga formu

**Files:**
- Create: `src/main/resources/db/migration/V136__voice_messages.sql`
- Create: `src/main/java/com/ltz/messaging/application/service/Waveform.java`
- Modify: `domain/enums/MessageType.java` (`VOICE`; `boolean hasAttachment()` → `IMAGE`, `VOICE`)
- Modify: `domain/entity/MessageAttachment.java` (`width/height/frameCount` → `Integer`; yeni `Integer durationMs`, `byte[] waveform` (getter kopya döner); `static uploadVoice(UUID conversationId, UUID uploaderId, AttachmentBlob blob, int durationMs, byte[] waveform, Instant now)` → `mimeType "audio/webm"`; `forwardCopy` yeni alanları kopyalar)
- Modify: `infrastructure/persistence/repository/AttachmentMeta.java` + `MessageAttachmentRepository.META` (yeni alanlar)
- Modify: `application/service/AttachmentView.java`, `api/dto/response/AttachmentResponse.java` (`Integer width, height, frameCount`, yeni `Integer durationMs`, `List<Integer> waveform`)
- Modify: `application/service/AttachmentService.java` (`upload(UUID uploader, UUID conversationId, byte[] content, String contentType, String waveform)`: `audio/*` → `VoiceInspectorPort`, aksi `ImageInspectorPort` ve `waveform != null` ise `INVALID_WAVEFORM`)
- Modify: `api/controller/AttachmentController.java` (`consumes` + `audio/webm`, `audio/ogg`; `@RequestParam(required = false) String waveform`)
- Modify: `domain/exception/MessagingErrorCode.java` — genelleştirilen iletiler: `MESSAGE_CONTENT_REQUIRED` "A message needs a text body, an attachment or a GIF.", `CONFLICTING_MESSAGE_CONTENT` "A message can carry either an attachment or a GIF, not both.", `TOO_MANY_UPLOADS` "Too many files uploaded. Wait a moment and try again.", `TOO_MANY_PENDING_ATTACHMENTS` "Too many uploaded files are waiting to be sent."
- Test: `FlywayMigrationTest.java` (+), `src/test/java/com/ltz/messaging/application/service/WaveformTest.java`, `src/test/java/com/ltz/messaging/api/controller/VoiceUploadTest.java`; `AttachmentUploadTest` değişmeden geçmeli.

**Interfaces:**
- Consumes: Task 4 `VoiceInspectorPort`.
- Produces (DB, V136):
  - `messages`: `ck_messages_type` → `type in ('TEXT','IMAGE','GIF','VOICE')`; yeni `ck_messages_voice_body check (type <> 'VOICE' or body is null)`.
  - `message_attachments`: `width`, `height`, `frame_count` `drop not null`; `add duration_ms int`, `add waveform bytea`; `ck_message_attachments_mime` → `('image/png','image/jpeg','image/gif','audio/webm')`; `ck_message_attachments_dimensions` ve `ck_message_attachments_frames` kaldırılıp yerine `ck_message_attachments_kind check ((mime_type like 'image/%' and width between 1 and 4096 and height between 1 and 4096 and frame_count between 1 and 500 and duration_ms is null and waveform is null) or (mime_type = 'audio/webm' and width is null and height is null and frame_count is null and duration_ms between 500 and 121000 and (waveform is null or octet_length(waveform) = 64)))`.
- Produces (Java): `Waveform.parse(String raw) → byte[]` (`null` → `null`; tam 64 öğe, her biri `0..100`, yalnız rakamlar, boşluk yok; aksi `MessagingException(INVALID_WAVEFORM)`); `Waveform.toList(byte[]) → List<Integer>` (`null` → `null`).

- [ ] **Step 1: Testleri yaz**
  - `FlywayMigrationTest.sesEkiTurKurallariniDbUygular`: ses satırı `width` dolu → CHECK ihlali; `duration_ms` 499 / 121001 → ihlal; `waveform` 63 bayt → ihlal; `VOICE` mesajında `body` → ihlal; mevcut görsel satırı geçerli.
  - `WaveformTest`: 64 × `"50"` → 64 bayt; 63 ya da 65 öğe, `101`, `-1`, `" 5"`, `"5,"`, boş dizgi → `INVALID_WAVEFORM`; `null` → `null`.
  - `VoiceUploadTest.oggYuklenirWebmOlarakSaklanir`: `TestAudio.ogg(...)` (10 sn) + `Content-Type audio/ogg;codecs=opus` + 64'lük `waveform` → `201`, `mimeType audio/webm`, `durationMs` ≈ 10000 − preSkip/48, `waveform` 64 öğe, `width/height/frameCount` `null`; yükleyen indirir, gövde `1A45DFA3` ile başlar.
  - `VoiceUploadTest.dalgaFormuIsteğeBaglidir`: parametresiz → `waveform` `null`.
  - `VoiceUploadTest.gecersizDalgaFormu400`: 10 öğe → `400 INVALID_WAVEFORM`; görsel yüklemesinde `waveform` → `400 INVALID_WAVEFORM`.
  - `VoiceUploadTest.desteklenmeyenSesTuru415`: `audio/mp4` → `415`.
  - `VoiceUploadTest.sesVeGorselAyniOranSiniriniPaylasir`: 5 görsel + 5 ses → 11. yükleme (ses) `429 TOO_MANY_UPLOADS`.
  - `VoiceUploadTest.uzunSes422`: 125 sn → `422 AUDIO_DURATION_OUT_OF_RANGE`; bozuk dosya → `422 INVALID_AUDIO`.
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=FlywayMigrationTest,WaveformTest,VoiceUploadTest` → FAIL.
- [ ] **Step 3: Migration, entity, view/response, servis ve controller değişikliklerini yaz.**
- [ ] **Step 4: Koş** — aynı komut + `AttachmentUploadTest,MediaMessageTest` → PASS.

---

### Task 6: `VOICE` mesajı (gönderme, yeniden deneme, yanıt, iletme, silme, okuma yolları)

**Files:**
- Modify: `domain/entity/Message.java` (`static Message voice(UUID conversationId, long seq, UUID senderId, String clientMessageId, UUID replyToMessageId, Instant now)`; `forwardOf` türü kaynaktan zaten alır)
- Modify: `application/service/MessageService.java`
- Modify: `application/service/MessageDraft.java` (dokümantasyon: `attachmentId` görsel ya da ses; `type()` artık yalnız "eki var" anlamında `IMAGE` döndürmez — bkz. aşağı)
- Modify: `infrastructure/persistence/repository/MessageAttachmentRepository.java` (`@Query("select a.mimeType from MessageAttachment a where a.id = :id and a.conversationId = :conversationId and a.uploaderId = :uploaderId and a.messageId is null") Optional<String> findPendingMimeType(...)`)
- Modify: `application/service/MessageAttachments.java` (`view.type().hasAttachment()`), `application/service/MessageForwardService.java` (`source.getType().hasAttachment()`)
- Test: `src/test/java/com/ltz/messaging/api/controller/VoiceMessageTest.java`

**Interfaces:**
- Consumes: Task 5 (`MessageType.VOICE`, `hasAttachment()`, `uploadVoice`, `AttachmentView` alanları).
- Produces: davranış. Ayrıntı:
  - `MessageDraft.type()` değişmez (ek varsa `IMAGE` döner) ama ekli taslakta artık kesin tür değildir. Kesin tür `MessageService.insert`'te, sohbet kilidi alındıktan sonra `findPendingMimeType` ile çözülür: yoksa `422 ATTACHMENT_NOT_AVAILABLE`; `audio/webm` → `VOICE` (body doluysa `400 VOICE_CAPTION_NOT_ALLOWED`), aksi `IMAGE`. Mevcut koşullu `link` UPDATE'i yarış korumasıdır, aynen kalır.
  - `isRetry`: draft'ta `attachmentId` varsa `Message.isRetryOf`'a draft türü yerine `existing.getType()` geçirilir ve `existing.getType().hasAttachment()` şartı aranır; ekin aynı olduğu mevcut `findIdByMessageId` eşliğiyle denetlenir.
  - Kayıtlı açıklamasız sesli mesajın kimliğiyle açıklamalı tekrar, gövde farklı olduğundan mevcut kurala göre `409 CLIENT_MESSAGE_ID_REUSED` döner (test sabitler: `sesliMesajKimligiAciklamaylaTekrarlanirsa409`).

- [ ] **Step 1: Testleri yaz** (`VoiceMessageTest`, `IntegrationTestSupport` yardımcılarıyla)
  - `sesliMesajGonderilirGecmisteListedeVeAkistaGorunur`: yükle → `POST messages {attachmentId}` → `201`, `type VOICE`, `body null`, `attachment.durationMs`, `attachment.waveform`; alıcının geçmişinde, sohbet listesi `lastMessage`'ında ve SSE `message` olayında aynı alanlar.
  - `sesliMesajaAciklamaEklenemez`: `body: "x"` → `400 VOICE_CAPTION_NOT_ALLOWED`, `seq` ilerlemez, ek bağlanmamış kalır.
  - `sesliMesajYenidenDenenirseAyniMesajDoner`: aynı `clientMessageId` + `attachmentId` → `200`/aynı `id` (mevcut yeniden deneme yanıt kodu), tek mesaj.
  - `baskasininSesiGonderilemez`: başkasının yüklediği ses → `422 ATTACHMENT_NOT_AVAILABLE`.
  - `sesliMesajaYanitAlintidaVoiceTurunuGosterir`: `replyTo.type VOICE`, `snippet null`.
  - `sesliMesajIletilinceBaytKopyalanmaz`: iletme → hedefte `type VOICE`, aynı `durationMs`/`waveform`, `forwarded true`; `attachment_blobs` satır sayısı 1.
  - `herkestenSilinenSesinIcerigiGider`: herkesten sil → `deleted true`, `attachment null`, indirme `404`, `attachment_blobs` 0.
  - `benimSildigimSesiIndiremem`: benden sil → o kullanıcı için indirme `404`, diğeri `200`.
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=VoiceMessageTest` → FAIL.
- [ ] **Step 3: Değişiklikleri yaz.**
- [ ] **Step 4: Koş** — `.\mvnw.cmd test -Dtest=VoiceMessageTest,MediaMessageTest,MessageSendTest,MessageForwardTest,MessageDeleteForEveryoneTest,MessageReplyTest` → PASS.

---

### Task 7: Bayt aralıklı indirme

**Files:**
- Modify: `api/controller/AttachmentController.java` (`download` → `ResponseEntity<Resource>` gövdesi `ByteArrayResource`; Spring MVC `Range` başlığını `206`/`416` ile işler ve `Accept-Ranges: bytes` ekler; mevcut güvenlik başlıkları aynen)
- Test: `src/test/java/com/ltz/messaging/api/controller/AttachmentRangeTest.java`

**Interfaces:**
- Consumes: Task 5–6 (ses yükleme ve gönderme yardımcıları).
- Produces: davranış.

- [ ] **Step 1: Testleri yaz**
  - `aralikIstegine206Doner`: gönderilmiş ses, alıcı `Range: bytes=0-99` → `206`, `Content-Range: bytes 0-99/<N>`, 100 bayt = tam içeriğin ilk 100 baytı; `nosniff`, sandbox CSP, `no-store, private` başlıkları var.
  - `aralikliOlmayanIstekTamDosyaVeAcceptRangesDoner`: `200`, `Accept-Ranges: bytes`.
  - `gecersizAralik416`: `bytes=99999999-` → `416`.
  - `yetkisizAralikIstegi404`: sohbet dışı kullanıcı ve benden silen kullanıcı `Range` ile → `404 ATTACHMENT_NOT_FOUND`.
- [ ] **Step 2: Koş** — `.\mvnw.cmd test -Dtest=AttachmentRangeTest` → FAIL.
- [ ] **Step 3: Controller'ı değiştir.**
- [ ] **Step 4: Koş** — `.\mvnw.cmd test -Dtest=AttachmentRangeTest,AttachmentUploadTest` → PASS.

---

### Task 8: Belgeler ve teslim kontrolü

**Files:**
- Modify: `docs/api.md` → `## MESSAGING`: ortak kurallar (görev listesine LTZ-UMT-MSG-06 / MSG-21), yükleme kaydı (ses türleri, `waveform`, yeni hatalar, yanıt alanları), indirme kaydı (`Range`, `206`/`416`), gönderim kaydı (`VOICE`, açıklama yasağı), `MessageResponse`/`AttachmentResponse` alanları.
- Modify: `docs/database.md` §3.8 (V136), `docs/er-diagram.md` §9 (`message_attachments` yeni sütunlar), `docs/SECURITY.md` §13 (`[KARAR]` Messaging'e ses: kabul, yeniden paketleme, steganografi sınırı), `docs-security/messaging-service/NOTLAR.md`, `docs/folder-structure.md` (`infrastructure/storage/audio/`).
- Create: `docs-messaging/TASK-06.md` (TASK-05.md biçimi: sonuç, doğrulama tablosu, akış, kararlar, güvenlik, gereksinim → test → dosya, değişen dosyalar, açık noktalar; frontend notları: `audio/webm;codecs=opus`, `audioBitsPerSecond ≈ 32000`, 2:00'de dur, 64 değerlik dalga formu, eski Safari'de kayıt düğmesi gizli, oynatıcı erişilebilirliği).
- Yeni ENV anahtarı yok (`ltz.messaging.voice.*` yalnız `application.properties` varsayılanı).

- [ ] **Step 1:** Tüm messaging testleri — `.\mvnw.cmd test` → PASS (sayıyı rapora yaz).
- [ ] **Step 2:** `.\pre-push\pre-push.cmd messaging-service` (repo kökünden, `AUTH_SERVICE_URL=http://auth-service:8080`) → `BACKEND TEST CHECK PASSED`; bulgular giderilir.
- [ ] **Step 3:** Belgeleri yaz; `TASK-06.md` doğrulama tablosuna gerçek sonuçları işle (`pre-push full dev` ve SonarQube Umut'un commit öncesi akışı; koşulmadıysa NOT RUN).
- [ ] **Step 4:** Umut'a commit mesajı öner (`feature: LTZ-UMT-MSG-06 ...` + madde listesi, atıf satırı yok).
