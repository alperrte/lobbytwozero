package com.ltz.messaging.infrastructure.storage.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;

/** WebM Opus (Matroska alt kümesi): tek Opus izinin blokları alınır; geri kalan her öğe atlanır ya da reddedilir. */
class WebmOpusReaderTest {

    private static final int MAX_PACKETS = 48_400;
    private static final int SEGMENT = 0x18538067;
    private static final int TRACKS = 0x1654AE6B;

    @Test
    void gecerliWebmPaketleriVeBasligiOkunur() throws Exception {
        OpusStream stream = WebmOpusReader.read(TestAudio.webm(100), MAX_PACKETS);

        assertThat(stream.head()).isEqualTo(new OpusHead(1, 312));
        assertThat(stream.packets()).hasSize(100);
        assertThat(stream.packets().getFirst().bytes())
                .isEqualTo(TestAudio.packet(TestAudio.CONFIG_CELT_20MS, false, TestAudio.FRAME_BYTES));
    }

    @Test
    void boyutuBilinmeyenSegmentVeKumelerOkunur() throws Exception {
        byte[] file = new TestAudio.Webm(TestAudio.head(2, 3840), TestAudio.packets(120)).unknownSizes()
                .packetsPerCluster(40).tags(TestAudio.ascii("tag")).build();

        OpusStream stream = WebmOpusReader.read(file, MAX_PACKETS);

        assertThat(stream.head()).isEqualTo(new OpusHead(2, 3840));
        assertThat(stream.packets()).hasSize(120);
    }

    @Test
    void blockGroupIcindekiBlokOkunur() throws Exception {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).blockGroups().build();
        assertThat(WebmOpusReader.read(file, MAX_PACKETS).packets()).hasSize(10);
    }

    @Test
    void etiketVoidVeCuesAtlanir() throws Exception {
        byte[] tagMarker = "GIZLI-ETIKET".getBytes(StandardCharsets.US_ASCII);
        byte[] voidMarker = "GIZLI-VOID".getBytes(StandardCharsets.US_ASCII);
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).tags(tagMarker)
                .voidElement(voidMarker).seekHead(TestAudio.ascii("seek")).build();

        OpusStream stream = WebmOpusReader.read(file, MAX_PACKETS);

        assertThat(stream.packets()).hasSize(10)
                .noneMatch(packet -> TestAudio.contains(packet.bytes(), tagMarker)
                        || TestAudio.contains(packet.bytes(), voidMarker));
    }

    @Test
    void ikinciIzReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).extraVideoTrack().build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void opusOlmayanCodecReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).codecId("A_VORBIS").build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void gecersizOpusHeadReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 2, 312, 48_000, 0, 1), TestAudio.packets(10)).build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void matroskaDocTypeReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).docType("matroska").build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void laceliBlokReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10)).laced().build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void dosyaninKalanindanBuyukOgeReddedilir() {
        byte[] file = TestAudio.concat(TestAudio.ebmlHeader("webm"), TestAudio.elementHeader(SEGMENT, 100),
                TestAudio.elementHeader(TRACKS, 1L << 40), new byte[88]);
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);

        byte[] segmentTooLarge = TestAudio.concat(TestAudio.ebmlHeader("webm"),
                TestAudio.elementHeader(SEGMENT, 1L << 50));
        assertThatThrownBy(() -> WebmOpusReader.read(segmentTooLarge, MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void boyutuBilinmeyenBilinmeyenOgeReddedilir() {
        byte[] file = TestAudio.concat(TestAudio.ebmlHeader("webm"),
                TestAudio.unknownSize(SEGMENT, TestAudio.unknownSize(0x1254C367, new byte[10])));
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void derinIcIceBilinmeyenOgelerAtlanir() throws Exception {
        // Tanınmayan öğelerin içine inilmez: 200 seviye iç içe öğe, özyinelemesiz atlanır.
        byte[] nested = new byte[0];
        for (int level = 0; level < 200; level++) {
            nested = TestAudio.element(0x1254C367, nested);
        }
        byte[] valid = TestAudio.webm(5);
        byte[] header = TestAudio.ebmlHeader("webm");
        byte[] segmentPayload = Arrays.copyOfRange(valid, header.length + 4 + 8, valid.length);
        byte[] file = TestAudio.concat(header, TestAudio.element(SEGMENT, TestAudio.concat(nested, segmentPayload)));

        assertThat(WebmOpusReader.read(file, MAX_PACKETS).packets()).hasSize(5);
    }

    @Test
    void kesikDosyaReddedilir() {
        byte[] file = TestAudio.webm(20);
        assertThatThrownBy(() -> WebmOpusReader.read(Arrays.copyOf(file, file.length - 7), MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
        byte[] unknownSize = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(20)).unknownSizes().build();
        assertThatThrownBy(() -> WebmOpusReader.read(Arrays.copyOf(unknownSize, unknownSize.length - 7), MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void sondaFazlaBaytReddedilir() {
        byte[] file = new TestAudio.Webm(TestAudio.head(1, 312), TestAudio.packets(10))
                .trailing("GIZLI".getBytes(StandardCharsets.US_ASCII)).build();
        assertThatThrownBy(() -> WebmOpusReader.read(file, MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void izlerYoksaReddedilir() {
        byte[] noTracks = TestAudio.concat(TestAudio.ebmlHeader("webm"),
                TestAudio.element(SEGMENT, TestAudio.element(0x1F43B675, TestAudio.element(0xE7, TestAudio.uint(0)))));
        assertThatThrownBy(() -> WebmOpusReader.read(noTracks, MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void paketSiniriAsilirsaReddedilir() throws Exception {
        assertThat(WebmOpusReader.read(TestAudio.webm(10), 10).packets()).hasSize(10);
        assertThatThrownBy(() -> WebmOpusReader.read(TestAudio.webm(11), 10))
                .isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void webmOlmayanDosyaReddedilir() {
        assertThatThrownBy(() -> WebmOpusReader.read(new byte[0], MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> WebmOpusReader.read(TestAudio.ogg(5), MAX_PACKETS))
                .isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> WebmOpusReader.read(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x00},
                MAX_PACKETS)).isInstanceOf(MalformedAudioException.class);
    }
}
