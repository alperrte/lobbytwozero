package com.ltz.messaging.infrastructure.storage.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;

/** Ogg Opus (RFC 3533, RFC 7845): yalnız tek Opus akışının paketleri alınır; bozuk yapı reddedilir. */
class OggOpusReaderTest {

    private static final int MAX_PACKETS = 48_400;

    @Test
    void gecerliOggPaketleriVeBasligiOkunur() throws Exception {
        OpusStream stream = OggOpusReader.read(TestAudio.ogg(50), MAX_PACKETS);

        assertThat(stream.head()).isEqualTo(new OpusHead(1, 312));
        assertThat(stream.packets()).hasSize(50);
        assertThat(stream.packets().getFirst().bytes())
                .isEqualTo(TestAudio.packet(TestAudio.CONFIG_CELT_20MS, false, TestAudio.FRAME_BYTES));
        assertThat(stream.durationMillis()).isEqualTo((50L * 960 - 312) * 1000 / 48_000);
    }

    @Test
    void birdenCokSayfayaBolunmusPaketBirlesir() throws Exception {
        byte[] tags = TestAudio.tags("COMMENT=" + "x".repeat(600));
        List<byte[]> packets = new ArrayList<>(TestAudio.packets(3));
        byte[] large = TestAudio.packet(TestAudio.CONFIG_CELT_20MS, false, 299);
        packets.add(large);

        OpusStream stream = OggOpusReader.read(new TestAudio.Ogg(TestAudio.head(2, 312), tags, packets)
                .tagsSegmentsPerPage(1).packetsPerPage(2).build(), MAX_PACKETS);

        assertThat(stream.head().channels()).isEqualTo(2);
        assertThat(stream.packets()).hasSize(4);
        assertThat(stream.packets().getLast().bytes()).isEqualTo(large);
    }

    @Test
    void sayfaSiniriTamPaketSonunaDenkGelirseOkunur() throws Exception {
        // 255 x 2 baytlık etiket: ikinci sayfa yalnız 0 lacing ile paketi bitirir.
        byte[] tags = Arrays.copyOf(TestAudio.tags(), 510);
        OpusStream stream = OggOpusReader.read(new TestAudio.Ogg(TestAudio.head(1, 312), tags, TestAudio.packets(2))
                .tagsSegmentsPerPage(2).build(), MAX_PACKETS);
        assertThat(stream.packets()).hasSize(2);
    }

    @Test
    void eosOlmayanAkisOkunur() throws Exception {
        OpusStream stream = OggOpusReader.read(new TestAudio.Ogg(TestAudio.head(1, 312), TestAudio.tags(),
                TestAudio.packets(5)).withoutEndOfStream().build(), MAX_PACKETS);
        assertThat(stream.packets()).hasSize(5);
    }

    @Test
    void etiketlerAtilir() throws Exception {
        byte[] secret = "GIZLI-ETIKET".getBytes(StandardCharsets.US_ASCII);
        OpusStream stream = OggOpusReader.read(new TestAudio.Ogg(TestAudio.head(1, 312),
                TestAudio.tags("ARTIST=GIZLI-ETIKET"), TestAudio.packets(5)).build(), MAX_PACKETS);

        assertThat(stream.packets()).hasSize(5).noneMatch(packet -> TestAudio.contains(packet.bytes(), secret));
    }

    @Test
    void bozukCrcReddedilir() {
        byte[] file = new TestAudio.Ogg(TestAudio.head(1, 312), TestAudio.tags(), TestAudio.packets(5)).corruptCrc()
                .build();
        assertThatThrownBy(() -> OggOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void kesikSayfaReddedilir() {
        byte[] file = TestAudio.ogg(20);
        assertThatThrownBy(() -> OggOpusReader.read(Arrays.copyOf(file, file.length - 7), MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OggOpusReader.read(Arrays.copyOf(file, 30), MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void sondaFazlaBaytReddedilir() {
        byte[] file = new TestAudio.Ogg(TestAudio.head(1, 312), TestAudio.tags(), TestAudio.packets(5))
                .trailing("GIZLI".getBytes(StandardCharsets.US_ASCII)).build();
        assertThatThrownBy(() -> OggOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void ikinciAkisReddedilir() {
        byte[] file = new TestAudio.Ogg(TestAudio.head(1, 312), TestAudio.tags(), TestAudio.packets(5)).extraStream()
                .build();
        assertThatThrownBy(() -> OggOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void opusOlmayanAkisReddedilir() {
        byte[] vorbis = Arrays.copyOf("\u0001vorbis".getBytes(StandardCharsets.US_ASCII), 30);
        byte[] file = new TestAudio.Ogg(vorbis, TestAudio.tags(), TestAudio.packets(5)).build();
        assertThatThrownBy(() -> OggOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);

        byte[] noTags = new TestAudio.Ogg(TestAudio.head(1, 312), "NotTags!".getBytes(StandardCharsets.US_ASCII),
                TestAudio.packets(5)).build();
        assertThatThrownBy(() -> OggOpusReader.read(noTags, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void sesPaketiOlmayanAkisReddedilir() {
        byte[] file = new TestAudio.Ogg(TestAudio.head(1, 312), TestAudio.tags(), List.of()).build();
        assertThatThrownBy(() -> OggOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void paketSiniriAsilirsaReddedilir() throws Exception {
        assertThat(OggOpusReader.read(TestAudio.ogg(10), 10).packets()).hasSize(10);
        assertThatThrownBy(() -> OggOpusReader.read(TestAudio.ogg(11), 10))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void oggOlmayanDosyaReddedilir() {
        assertThatThrownBy(() -> OggOpusReader.read(new byte[100], MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OggOpusReader.read(new byte[0], MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
    }
}
