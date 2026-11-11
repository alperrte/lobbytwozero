package com.ltz.messaging.infrastructure.storage;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ltz.messaging.application.port.VoiceInspectorPort;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.AttachmentProperties;
import com.ltz.messaging.infrastructure.config.VoiceProperties;
import com.ltz.messaging.infrastructure.storage.audio.MalformedAudioException;
import com.ltz.messaging.infrastructure.storage.audio.OggOpusReader;
import com.ltz.messaging.infrastructure.storage.audio.OpusStream;
import com.ltz.messaging.infrastructure.storage.audio.WebmOpusReader;
import com.ltz.messaging.infrastructure.storage.audio.WebmOpusWriter;

/**
 * Sesli mesaj doğrulama ve yeniden paketleme (MSG-21, SECURITY.md §13); saf Java, yeni bağımlılık yok, ses çözülmez:
 * <ul>
 *   <li>Biçim ilk baytlardan belirlenir (Ogg {@code OggS}, WebM EBML {@code 1A45DFA3}) ve bildirilen
 *       {@code Content-Type} ile aynı olmalıdır.</li>
 *   <li>Kapsayıcı ayrıştırılır, tek Opus akışının paketleri RFC 6716'ya göre doğrulanır, dolguları atılır.</li>
 *   <li>Süre paket örneklerinden hesaplanır (kapsayıcının beyanı kullanılmaz) ve sınırlar içinde olmalıdır.</li>
 *   <li>Çıktı sıfırdan yazılan WebM'dir: etiketler, ek izler/akışlar, bilinmeyen öğeler ve sondaki baytlar taşınmaz.</li>
 * </ul>
 * İşlem girdi boyutuyla doğrusaldır ve 1 MB'lık dosyada milisaniyeler sürer; ancak istek başına birkaç MB geçici bellek
 * kullandığından aynı anda en çok {@code concurrency} ses işlenir (görselle aynı ayar), sıra uzarsa {@code MEDIA_BUSY}.
 * Ses verisinin içine gömülü bilgi (steganografi) yeniden kodlama olmadan kaldırılamaz (SECURITY.md §13).
 */
@Component
public class OpusVoiceInspector implements VoiceInspectorPort {

    static final String OGG = "audio/ogg";
    static final String WEBM = "audio/webm";
    /** En kısa Opus paketi 2,5 ms'dir; azami süreye sığabilecek paket sayısı. */
    private static final double MIN_PACKET_MILLIS = 2.5;

    private final VoiceProperties properties;
    private final Duration acquireTimeout;
    private final Semaphore slots;

    /** Eşzamanlılık sınırı görselle aynı ayardan ({@code ltz.messaging.attachments.concurrency}); yuvalar ayrı. */
    @Autowired
    public OpusVoiceInspector(VoiceProperties properties, AttachmentProperties attachments) {
        this(properties, attachments.acquireTimeout(), new Semaphore(attachments.concurrency(), true));
    }

    /** Testte işleme yuvaları dışarıdan tutulabilsin diye. */
    OpusVoiceInspector(VoiceProperties properties, Duration acquireTimeout, Semaphore slots) {
        this.properties = properties;
        this.acquireTimeout = acquireTimeout;
        this.slots = slots;
    }

    @Override
    public ProcessedVoice process(byte[] content, String declaredContentType) {
        String detected = requireSupported(content, declaredContentType);
        acquireSlot();
        try {
            return repackage(content, detected);
        } finally {
            slots.release();
        }
    }

    /** Ayrıştırma ve yazma: istek başına birkaç MB geçici bellek, bu yüzden eşzamanlılığı sınırlıdır. */
    private ProcessedVoice repackage(byte[] content, String detected) {
        OpusStream stream = read(content, detected);
        long duration = stream.durationMillis();
        if (duration < properties.minDuration().toMillis() || duration > properties.maxAcceptedMillis()) {
            throw new MessagingException(MessagingErrorCode.AUDIO_DURATION_OUT_OF_RANGE);
        }
        byte[] packed = WebmOpusWriter.write(stream);
        if (packed.length > MessageAttachment.MAX_SIZE_BYTES) {
            throw new MessagingException(MessagingErrorCode.AUDIO_TOO_LARGE);
        }
        return new ProcessedVoice(WEBM, packed, (int) duration);
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new MessagingException(MessagingErrorCode.MEDIA_BUSY);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new MessagingException(MessagingErrorCode.MEDIA_BUSY);
        }
    }

    /** Boyut ve biçim: içerikten belirlenen tür bildirilen türle aynı olmalı. */
    private static String requireSupported(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new MessagingException(MessagingErrorCode.INVALID_AUDIO);
        }
        if (content.length > MessageAttachment.MAX_SIZE_BYTES) {
            throw new MessagingException(MessagingErrorCode.AUDIO_TOO_LARGE);
        }
        String detected = detect(content);
        if (detected == null || !detected.equals(MediaTypes.normalize(declaredContentType))) {
            throw new MessagingException(MessagingErrorCode.INVALID_AUDIO);
        }
        return detected;
    }

    private OpusStream read(byte[] content, String detected) {
        int maxPackets = (int) (properties.maxAcceptedMillis() / MIN_PACKET_MILLIS);
        try {
            return OGG.equals(detected) ? OggOpusReader.read(content, maxPackets)
                    : WebmOpusReader.read(content, maxPackets);
        } catch (MalformedAudioException _) {
            // Ayrıntı istemciye ve loga yansıtılmaz.
            throw new MessagingException(MessagingErrorCode.INVALID_AUDIO);
        }
    }

    static String detect(byte[] content) {
        if (content.length >= 4 && content[0] == 'O' && content[1] == 'g' && content[2] == 'g' && content[3] == 'S') {
            return OGG;
        }
        if (content.length >= 4 && (content[0] & 0xFF) == 0x1A && (content[1] & 0xFF) == 0x45
                && (content[2] & 0xFF) == 0xDF && (content[3] & 0xFF) == 0xA3) {
            return WEBM;
        }
        return null;
    }
}
