# LTZ-UMT-MSG-06 — Sesli Mesaj (TASK-06)

> Tarih: 2026-11-10 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-21 · Durum: **kod, testler ve belgeler tamam; `pre-push messaging-service` PASSED; gerçek Chrome kaydıyla yeniden paketleme ve çalma doğrulandı; DEV stack, `pre-push full dev` ve SonarQube koşulmadı**
>
> Tasarım: `docs-messaging/TASK-06-TASARIM.md` · Plan: `docs-messaging/TASK-06-PLAN.md` · Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING` ("Mesaj eki yükle", "Mesaj ekini indir / sesi dinle", "Mesaj gönder"); şema `docs/database.md` §3.8, `docs/er-diagram.md` §9; güvenlik `docs/SECURITY.md` §13, `docs-security/messaging-service/NOTLAR.md`.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-21 Sesli mesaj | IMPLEMENTED (backend) | Yalnız Opus (WebM/Ogg), ≤ 1 MB, 0,5 sn – 2 dk; sunucu yalnız Opus paketlerini alıp sıfırdan WebM yazar; `VOICE` mesajı; süre ve dalga formu; bayt aralıklı dinleme; yanıt/iletme/tepki/silme/SSE sesli mesajda da çalışır |

Doğrulama (2026-11-10):

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, PostgreSQL 17) | PASS — 388 test, 0 hata, 0 atlanan (bu görevde 83 yeni test) |
| `.\pre-push\pre-push.cmd messaging-service` (`AUTH_SERVICE_URL=http://auth-service:8080`) | **BACKEND TEST CHECK PASSED** — 388 test, JaCoCo satır %97,23, değişen satır %97,25 (1874 / 1927; `origin/main`'e göre, MSG-04/05 dahil), Checkstyle/PMD yeni ihlal 0. İlk koşuda PMD `CyclomaticComplexity` (eşik 15) üç metotta aşıldı: `OpusVoiceInspector.process`, `OggOpusReader.read`, `OpusPacket.code3` yardımcılara bölündü (davranış aynı). Dünden kalan bayat `test-results/quality.lock` (sahibi süreç yoktu) silindi |
| Bağımsız kod incelemesi (tüm değişiklik) | Critical 0, Important 1: V136 `ck_message_attachments_kind` NULL'ları geçiriyordu (görsel ölçüleri ve ses süresi DB'de zorunlu değildi) → `is not null` eklendi, `FlywayMigrationTest`'e NULL satırları eklendi. Ses işlemenin istek başına birkaç MB bellek kullanması (Minor'dan yükseltildi) → aynı anda en çok 2 ses (`MEDIA_BUSY`). Kalan küçük notlar §7'de ya da sonraki iş |
| Gerçek tarayıcı kaydı (Chrome 157 `MediaRecorder`, `audio/webm;codecs=opus`, 32 kbps, 3 sn osilatör; boyutu bilinmeyen Segment/Cluster) → `OpusVoiceInspector` → Chrome'da `<audio>` ve `decodeAudioData` | PASS — kabul edildi, `durationMillis` 2940; girdinin süresi tarayıcıda bilinmiyor (`Infinity`), çıktınınki 2,94 sn; 2. saniyeye sarma çalışıyor; çıktı 2 kanal, 2,94 sn çözülüyor. Geçici doğrulama testi teslimden önce silindi |
| `.\pre-push\pre-push.cmd full dev` | NOT RUN — Umut'un commit öncesi akışı |
| SonarQube (`sonar.cmd scan dev`) | NOT RUN — Umut'un commit öncesi akışı |
| DEV stack (yeni imaj, V136 gerçek `messaging_db`'ye), Gateway + tarayıcı ile kimlikli yükleme/dinleme | NOT VERIFIED |
| Firefox Ogg kaydı, Safari'de çalma | NOT VERIFIED (Ogg yalnız kodda üretilen dosyalarla test edildi) |

## 2. Akış

**Yükleme** `POST /conversations/{id}/attachments` (`Content-Type: audio/webm` ya da `audio/ogg`, isteğe bağlı `?waveform=` 64 değer):
1. `waveform` biçimi denetlenir (görselde verilirse `400`), kısa transaction'da aktif üye + ortak oran (10/dk) + bekleyen (≤ 10).
2. Transaction **dışında** `OpusVoiceInspector`: boyut ≤ 1 MB → magic byte ↔ `Content-Type` → `OggOpusReader` ya da `WebmOpusReader` yalnız tek Opus akışının başlık değerlerini ve paketlerini çıkarır; her paket `OpusPacket` ile RFC 6716'ya göre doğrulanır ve dolgusu atılır → süre = Σ örnek − pre-skip (0,5 sn – 2 dk + 1 sn) → `WebmOpusWriter` sıfırdan WebM yazar (standart `OpusHead`, `Duration`, kümelerden önce `Cues`, ≤ 5 sn kümeler) → çıktı ≤ 1 MB.
3. İkinci transaction: denetimler yeniden, `attachment_blobs` + `message_attachments` (`audio/webm`, `duration_ms`, `waveform`).

**Gönderme** `POST .../messages {clientMessageId, attachmentId}`: sohbet kilidi alındıktan sonra tür bağlanacak dosyadan çözülür (`findPendingMimeType`): ses → `VOICE` (`body` varsa `400 VOICE_CAPTION_NOT_ALLOWED`), görsel → `IMAGE`; yoksa `422 ATTACHMENT_NOT_AVAILABLE`. Bağlama mevcut koşullu UPDATE'tir. Yeniden denemede tür kayıtlı mesajdan alınır.

**Dinleme** `GET .../attachments/{id}`: erişim kuralı görselle aynı; yanıt `ByteArrayResource` olduğu için Spring `Range` isteğini `206`/`416` ile karşılar, `Accept-Ranges: bytes`; yetki aralıktan önce denetlenir.

**Diğer işlemler:** iletme (`MessageType.hasAttachment()` ile ek kopyası; bayt kopyalanmaz, süre ve dalga formu taşınır), herkesten/benden silme, tepki, okundu, SSE ve `lastMessage` değişmeden çalışır; okuma modeli (`MessageAttachments`) `IMAGE` ve `VOICE` eklerini tek sorguda ekler.

## 3. Kararlar ve varsayımlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Biçim, sınırlar, yeniden paketleme | Opus (WebM/Ogg), 2 dk, 1 MB; yalnız ses paketleri yeniden paketlenir (Umut'un kararı) | Gizlenmiş içerik kalmasın |
| Çıktı biçimi | Her zaman WebM (Ogg girdi de) — Claude'un kararı, Umut onayladı | Tek yazıcı, tek oynatma yolu; Chrome'un kaydında olmayan süre ve ileri sarma düzelir |
| Süre sınırları | 0,5 sn – 2 dk, üst sınıra 1 sn tolerans (onaylandı) | 2:00'de durdurulan kaydın birkaç ms fazlası reddedilmesin |
| Dalga formu | İstemciden, isteğe bağlı, 64 × 0–100 (onaylandı) | Sunucu sesi çözmez; dosyayı indirmeden çizilebilsin |
| Açıklama | Sesli mesajda yok (onaylandı) | WhatsApp davranışı |
| "Dinlendi" bilgisi | Bu görevde yok (onaylandı) | PDA istemiyor; ayrı tablo/uç/olay gerektirir |
| Oran sınırı | Görselle ortak (onaylandı) | Tek yükleme bütçesi |
| Tür | İstemciden alınmaz, dosyadan; aynı `attachmentId` alanı (onaylandı) | İstemcinin bildirdiği türe güvenilmez |
| Steganografi | Kabul edilen sınır, belgelendi (onaylandı) | Yeniden kodlama yerel bağımlılık ister |
| Ayrıştırıcı | Saf Java, yeni bağımlılık yok; tanınmayan öğelerin içine inilmez (özyineleme yok); WebM laced blok reddedilir | Bağımlılık yüzeyi büyümez; derinlik saldırısı yapısal olarak yok; tarayıcılar lacing kullanmaz |
| Eşzamanlılık | Aynı anda en çok 2 ses (görselle aynı `concurrency`/`acquire-timeout` ayarı, ayrı yuvalar), sıra dolarsa `503 MEDIA_BUSY` | Ayrıştırma hızlı (1 MB'lık kötü niyetli dosya < 500 ms, testli) ama istek başına birkaç MB geçici bellek kullanır; son incelemede eklendi |
| Hata iletileri | `MESSAGE_CONTENT_REQUIRED`, `CONFLICTING_MESSAGE_CONTENT`, `TOO_MANY_UPLOADS`, `TOO_MANY_PENDING_ATTACHMENTS` iletileri "attachment/files" olarak genelleşti | Kodlar ve durumlar aynı |
| ENV | Yeni anahtar yok (`ltz.messaging.voice.*` yalnız `application.properties`) | — |

## 4. Güvenlik

| Konu | Uygulama |
| --- | --- |
| Gizli içerik | Çıktı sıfırdan yazılır; `OpusTags`, WebM `Tags`/`Void`/`SeekHead`/`Attachments`, ek iz/akış, sayfa arası ve sondaki bayt, paket dolgusu, başlıktaki kazanç/giriş hızı çıktıya geçmez (`gizliIcerikCiktidaKalmaz`) |
| Kötü niyetli dosya | Öğe boyutu ebeveyn sınırını aşamaz (2^40 boyut → `INVALID_AUDIO`); boyutu bilinmeyen öğe yalnız Segment/Cluster; paket sayısı sınırlı; Ogg CRC ve sayfa sırası; 1 MB'lık dosya < 500 ms; ayrıntı ve yığın izi yok |
| Süre/boyut | İçerikten hesaplanır; kapsayıcı beyanı ve `Content-Length` kullanılmaz; DB CHECK `duration_ms` 500–121000, `waveform` 64 bayt |
| Erişim | Görselle aynı (mesajı görebilen aktif üye, gönderilmemişse yalnız yükleyen); `Range` istekleri dahil yetki her istekte aralıktan önce (`yetkisizAralikIstegi404`) |
| Sunum | `nosniff`, `inline`, `default-src 'none'; sandbox`, `no-store, private` (206 yanıtında da) |
| Bilinen sınır | Ses verisinin içine gömülü bilgi (steganografi) kaldırılmaz; yalnız yeniden kodlama (libopus/ffmpeg) kaldırır |
| Gizlilik | Ses içeriği ve dalga formu loglanmaz; `MessageSent` içerik taşımaz |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| Opus paket/başlık | `OpusPacketTest` (9), `OpusHeadTest` (4) | `OpusPacket`, `OpusHead`, `OpusStream`, `MalformedAudioException` |
| Ogg okuma | `OggOpusReaderTest` (13) | `OggOpusReader` |
| WebM okuma | `WebmOpusReaderTest` (17) | `WebmOpusReader`, `Ebml` |
| WebM yazma, doğrulama, sınırlar, eşzamanlılık | `WebmOpusWriterTest` (3), `OpusVoiceInspectorTest` (10), `VoicePropertiesTest` (2) | `WebmOpusWriter`, `OpusVoiceInspector`, `VoiceInspectorPort`, `VoiceProperties`, `MediaTypes` |
| Şema V136 (NULL'lar da CHECK'e takılır) | `FlywayMigrationTest` (+1) | `V136__voice_messages.sql` |
| Yükleme, dalga formu | `VoiceUploadTest` (8), `WaveformTest` (3) | `AttachmentService`, `AttachmentController`, `Waveform`, `MessageAttachment`, `AttachmentMeta`, `AttachmentView`, `AttachmentResponse` |
| `VOICE` mesajı | `VoiceMessageTest` (9) | `MessageService`, `Message.voice`, `MessageType`, `MessageAttachments`, `MessageForwardService`, `MessageAttachmentRepository` |
| Bayt aralığı | `AttachmentRangeTest` (4) | `AttachmentController` |

Öne çıkan kanıtlar: işaret baytı girdinin her gizlenebilir yerine konunca çıktıda hiçbir ofsette yok (`gizliIcerikCiktidaKalmaz`); Chrome biçimi (boyutu bilinmeyen Segment/Cluster) okunur (`boyutuBilinmeyenSegmentVeKumelerOkunur`); girdi ≤ 1 MB iken WebM çıktısı > 1 MB olursa reddedilir (`ciktiBirMegabaytiAsarsaReddedilir`); açıklamalı sesli mesaj `seq` tüketmez ve sesi bağlamaz (`sesliMesajaAciklamaEklenemez`); sesli mesaj yeniden denemesi aynı mesajı döner (`sesliMesajYenidenDenenirseAyniMesajDoner`); iletmede bayt kopyalanmaz (`sesliMesajIletilinceBaytKopyalanmaz`); benden silen aralık isteğiyle de indiremez (`yetkisizAralikIstegi404`).

## 6. Değişen dosyalar

- `backend/services/messaging-service`: `V136__voice_messages.sql`; yeni `infrastructure/storage/audio/` (`OpusPacket`, `OpusHead`, `OpusStream`, `MalformedAudioException`, `OggOpusReader`, `WebmOpusReader`, `WebmOpusWriter`, `Ebml`), `infrastructure/storage/OpusVoiceInspector`, `MediaTypes` (görselle ortak), `application/port/VoiceInspectorPort`, `infrastructure/config/VoiceProperties`, `application/service/Waveform`; değişen `ImageIoInspector`, `MessageType` (`VOICE`, `hasAttachment`), `Message` (`voice`), `MessageAttachment` (`uploadVoice`, `durationMs`, `waveform`, nullable ölçüler), `AttachmentMeta`, `MessageAttachmentRepository` (`findPendingMimeType`), `AttachmentView`, `AttachmentResponse`, `AttachmentService`, `AttachmentController` (ses türleri, `waveform`, `Range`), `MessageService`, `MessageDraft`, `MessageAttachments`, `MessageForwardService`, `MessagingErrorCode`, `MessagingApplicationConfig`, `application.properties`; testler (`TestAudio` dahil).
- `docs/api.md` MESSAGING (ortak kurallar, gönderim, ek yükleme, ek indirme), `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs/SECURITY.md` §13, `docs/folder-structure.md`, `docs-security/messaging-service/NOTLAR.md`, `docs-messaging/TASK-06-TASARIM.md`, `docs-messaging/TASK-06-PLAN.md`.

## 7. Açık noktalar

1. **İndirme her `Range` isteğinde içeriğin tamamını (≤ 1 MB) DB'den okur**; Safari bir sesi birkaç aralık isteğiyle çalar. Doğru çalışır, DB okuması istek sayısıyla çoğalır; nesne depolamaya geçişte (madde 7) ele alınmalı.
2. **Frontend yok.** Kayıt için `new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus', audioBitsPerSecond: 32000 })` (Firefox'ta `audio/ogg;codecs=opus` da olur); **varsayılan bit hızında 2 dk 1 MB'ı aşar**. Kayıt 2:00'de durdurulur; dalga formu kayıt sırasında 64 değere (0–100) indirilip `?waveform=` ile gönderilir. `MediaRecorder` Opus kaydedemeyen (yalnız AAC/MP4 kaydeden eski Safari) tarayıcıda kayıt düğmesi gizlenir. Oynatıcı klavye ve ekran okuyucuyla erişilebilir olmalı (`web-rules.md`); süre `durationMs`'ten gösterilir.
3. **DEV stack + Gateway + tarayıcı** ile kimlikli yükleme ve `<audio>` ile dinleme (çerez → Bearer, `Range`) doğrulanmadı. Gateway'in `Range`/`206` yanıtını olduğu gibi geçirdiği teyit edilmeli.
4. **Firefox Ogg kaydı ve Safari'de çalma** gerçek tarayıcıyla doğrulanmadı.
5. **Steganografi** (ses verisine gömülü bilgi) kaldırılmaz; gerekiyorsa sunucuda yeniden kodlama ayrı karar (yerel bağımlılık).
6. **"Dinlendi" bilgisi** yok; istenirse ayrı görev.
7. **Depolama** hâlâ DB'de (`attachment_blobs`); ses de aynı tabloda. Nesne depolamaya geçiş ekiple konuşulacak.
8. **Hesap silme iç ucu** hâlâ yok (TASK-02'den).
