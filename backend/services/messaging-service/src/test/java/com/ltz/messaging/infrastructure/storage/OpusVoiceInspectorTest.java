package com.ltz.messaging.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;
import com.ltz.messaging.application.port.VoiceInspectorPort.ProcessedVoice;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.VoiceProperties;
import com.ltz.messaging.infrastructure.storage.audio.WebmOpusReader;

/**
 * Sesli mesaj doğrulama ve yeniden paketleme (MSG-21): yalnız Opus paketleri yeni bir WebM'e yazılır; süre ve boyut
 * sınırı içerikten hesaplanır; kötü niyetli dosya hızlı reddedilir.
 */
class OpusVoiceInspectorTest {

    private static final byte[] MARKER = "LTZ-GIZLI-ISARET".getBytes(StandardCharsets.US_ASCII);
    private static final String OGG = "audio/ogg";
    private static final String WEBM = "audio/webm";

    private final OpusVoiceInspector inspector = new OpusVoiceInspector(
            new VoiceProperties(Duration.ofMillis(500), Duration.ofMinutes(2), Duration.ofSeconds(1)),
            Duration.ofSeconds(2), new Semaphore(2));

    @Test
    void oggGirdiWebmCiktiyaPaketlenir() throws Exception {
        ProcessedVoice voice = inspector.process(TestAudio.ogg(1500), "audio/ogg; codecs=opus");

        assertThat(voice.mimeType()).isEqualTo(WEBM);
        assertThat(voice.durationMillis()).isEqualTo((1500 * 960 - 312) * 1000 / 48_000);
        assertThat(voice.content()).startsWith(0x1A, 0x45, 0xDF, 0xA3);
        assertThat(WebmOpusReader.read(voice.content(), 48_400).packets()).hasSize(1500);
    }

    @Test
    void webmGirdiYenidenYazilir() {
        byte[] input = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(100)).unknownSizes().build();
        ProcessedVoice voice = inspector.process(input, "audio/webm;codecs=opus");
        assertThat(voice.mimeType()).isEqualTo(WEBM);
        assertThat(voice.content()).isNotEqualTo(input);
    }

    @Test
    void gizliIcerikCiktidaKalmaz() {
        List<byte[]> packets = new ArrayList<>(TestAudio.packets(50));
        packets.add(TestAudio.paddedPacket(TestAudio.CONFIG_CELT_20MS, 1, 20, MARKER));
        byte[] webm = new TestAudio.Webm(TestAudio.head(1, 312), packets).tags(MARKER).voidElement(MARKER)
                .seekHead(MARKER).build();
        byte[] ogg = new TestAudio.Ogg(TestAudio.head(1, 1, 312, 44_100, 1536, 0),
                TestAudio.tags("COMMENT=LTZ-GIZLI-ISARET"), packets).build();

        byte[] fromWebm = inspector.process(webm, WEBM).content();
        byte[] fromOgg = inspector.process(ogg, OGG).content();

        assertThat(TestAudio.contains(fromWebm, MARKER)).isFalse();
        assertThat(TestAudio.contains(fromOgg, MARKER)).isFalse();
        // Kazanç ve giriş hızı sıfırdan yazılır: çıktıdaki başlık, girdi başlığı değil standart başlıktır.
        assertThat(TestAudio.contains(fromOgg, TestAudio.head(1, 312))).isTrue();
        assertThat(TestAudio.contains(fromOgg, TestAudio.head(1, 1, 312, 44_100, 1536, 0))).isFalse();
    }

    @Test
    void bildirilenTurIcerikleUyusmazsaReddedilir() {
        assertCode(() -> inspector.process(TestAudio.ogg(50), WEBM), MessagingErrorCode.INVALID_AUDIO);
        assertCode(() -> inspector.process(TestAudio.webm(50), OGG), MessagingErrorCode.INVALID_AUDIO);
        assertCode(() -> inspector.process(TestAudio.webm(50), null), MessagingErrorCode.INVALID_AUDIO);
        assertThat(inspector.process(TestAudio.ogg(50), "Audio/OGG; codecs=opus").mimeType()).isEqualTo(WEBM);
    }

    @Test
    void bozukYaDaBosDosyaReddedilir() {
        assertCode(() -> inspector.process(new byte[0], WEBM), MessagingErrorCode.INVALID_AUDIO);
        assertCode(() -> inspector.process(new byte[] {'O', 'g', 'g', 'S', 1}, OGG), MessagingErrorCode.INVALID_AUDIO);
        assertCode(() -> inspector.process("RIFF....WAVE".getBytes(StandardCharsets.US_ASCII), WEBM),
                MessagingErrorCode.INVALID_AUDIO);
    }

    @Test
    void sureSinirlari() {
        assertCode(() -> inspector.process(ogg(20), OGG), MessagingErrorCode.AUDIO_DURATION_OUT_OF_RANGE);
        assertThat(inspector.process(ogg(25), OGG).durationMillis()).isEqualTo(500);
        assertThat(inspector.process(ogg(6050), OGG).durationMillis()).isEqualTo(121_000);
        assertCode(() -> inspector.process(ogg(6051), OGG), MessagingErrorCode.AUDIO_DURATION_OUT_OF_RANGE);
    }

    @Test
    void birMegabaytUstuReddedilir() {
        assertCode(() -> inspector.process(new byte[1_048_577], WEBM), MessagingErrorCode.AUDIO_TOO_LARGE);
    }

    @Test
    void ciktiBirMegabaytiAsarsaReddedilir() {
        List<byte[]> packets = new ArrayList<>();
        for (int index = 0; index < 6050; index++) {
            packets.add(TestAudio.packet(TestAudio.CONFIG_CELT_20MS, false, 169));
        }
        byte[] input = new TestAudio.Ogg(TestAudio.head(1, 0), TestAudio.tags(), packets).build();
        assertThat(input.length).isLessThanOrEqualTo(1_048_576);

        assertCode(() -> inspector.process(input, OGG), MessagingErrorCode.AUDIO_TOO_LARGE);
    }

    @Test
    void birMegabaytlikKotuNiyetliDosyaHizliReddedilir() {
        List<byte[]> tiny = new ArrayList<>();
        for (int index = 0; index < 400_000; index++) {
            tiny.add(new byte[] {(byte) (16 << 3)});
        }
        byte[] manyPackets = new TestAudio.Ogg(TestAudio.head(1, 0), TestAudio.tags(), tiny).packetsPerPage(255)
                .build();
        ByteArrayOutputStream voids = new ByteArrayOutputStream();
        while (voids.size() < 1_000_000) {
            voids.write(0xEC);
            voids.write(0x80);
        }
        byte[] manyElements = TestAudio.concat(TestAudio.ebmlHeader("webm"),
                TestAudio.element(0x18538067, voids.toByteArray()));
        assertThat(manyPackets.length).isLessThanOrEqualTo(1_048_576);
        assertThat(manyElements.length).isLessThanOrEqualTo(1_048_576);

        assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
            assertCode(() -> inspector.process(manyPackets, OGG), MessagingErrorCode.INVALID_AUDIO);
            assertCode(() -> inspector.process(manyElements, WEBM), MessagingErrorCode.INVALID_AUDIO);
        });
    }

    @Test
    void islemeYuvasiDoluysaMediaBusy() {
        OpusVoiceInspector busy = new OpusVoiceInspector(
                new VoiceProperties(Duration.ofMillis(500), Duration.ofMinutes(2), Duration.ofSeconds(1)),
                Duration.ofMillis(50), new Semaphore(0));
        assertCode(() -> busy.process(TestAudio.ogg(50), OGG), MessagingErrorCode.MEDIA_BUSY);

        Semaphore slots = new Semaphore(1);
        OpusVoiceInspector free = new OpusVoiceInspector(
                new VoiceProperties(Duration.ofMillis(500), Duration.ofMinutes(2), Duration.ofSeconds(1)),
                Duration.ofMillis(50), slots);
        assertThat(free.process(TestAudio.ogg(50), OGG).mimeType()).isEqualTo(WEBM);
        assertCode(() -> free.process(TestAudio.ogg(5), OGG), MessagingErrorCode.AUDIO_DURATION_OUT_OF_RANGE);
        assertThat(slots.availablePermits()).isEqualTo(1);
    }

    private static byte[] ogg(int packets) {
        return new TestAudio.Ogg(TestAudio.head(1, 0), TestAudio.tags(), TestAudio.packets(packets)).build();
    }

    private static void assertCode(Runnable call, MessagingErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MessagingException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
