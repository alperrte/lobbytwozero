package com.ltz.messaging.infrastructure.storage.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;

/** RFC 6716 §3 paket yapısı: süre TOC'tan, dolgu atılır, kurala uymayan paket reddedilir. */
class OpusPacketTest {

    @Test
    void kod0PaketSuresiConfigdenHesaplanir() throws Exception {
        assertThat(OpusPacket.parse(TestAudio.packet(3, false, 20)).samples()).isEqualTo(2880);
        assertThat(OpusPacket.parse(TestAudio.packet(16, false, 20)).samples()).isEqualTo(120);
        assertThat(OpusPacket.parse(TestAudio.packet(15, true, 20)).samples()).isEqualTo(960);
        assertThat(OpusPacket.parse(TestAudio.packet(0, false, 20)).samples()).isEqualTo(480);
        assertThat(OpusPacket.parse(TestAudio.packet(12, false, 20)).samples()).isEqualTo(480);
    }

    @Test
    void kod0PaketDegismedenKorunur() throws Exception {
        byte[] raw = TestAudio.packet(31, false, 40);
        assertThat(OpusPacket.parse(raw).bytes()).isEqualTo(raw);
    }

    @Test
    void kod1IkiEsitCerceveSayar() throws Exception {
        byte[] even = {(byte) (31 << 3 | 1), 1, 2, 3, 4};
        assertThat(OpusPacket.parse(even).samples()).isEqualTo(2 * 960);
        byte[] odd = {(byte) (31 << 3 | 1), 1, 2, 3};
        assertThatThrownBy(() -> OpusPacket.parse(odd)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void kod2UzunlukTasarsaReddedilir() throws Exception {
        byte[] overflow = {(byte) (31 << 3 | 2), 10, 1, 2, 3};
        assertThatThrownBy(() -> OpusPacket.parse(overflow)).isInstanceOf(MalformedAudioException.class);

        // 2 baytlık uzunluk: 252 + 4 * 1 = 256 baytlık ilk çerçeve, ardından 3 baytlık ikinci çerçeve.
        byte[] twoByteLength = new byte[1 + 2 + 256 + 3];
        twoByteLength[0] = (byte) (31 << 3 | 2);
        twoByteLength[1] = (byte) 252;
        twoByteLength[2] = 1;
        assertThat(OpusPacket.parse(twoByteLength).samples()).isEqualTo(2 * 960);

        byte[] truncatedLength = {(byte) (31 << 3 | 2), (byte) 252};
        assertThatThrownBy(() -> OpusPacket.parse(truncatedLength)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void kod3DolgusuAtilirCerceveleriKorunur() throws Exception {
        byte[] secret = "GIZLI".getBytes(StandardCharsets.US_ASCII);
        OpusPacket packet = OpusPacket.parse(TestAudio.paddedPacket(31, 3, 10, secret));

        assertThat(TestAudio.contains(packet.bytes(), secret)).isFalse();
        assertThat(packet.bytes()[1] & 0x40).isZero();
        assertThat(packet.bytes()).hasSize(2 + 30);
        assertThat(packet.samples()).isEqualTo(3 * 960);
        // Dolgusuz paket yeniden ayrıştırılınca aynı kalır.
        assertThat(OpusPacket.parse(packet.bytes()).bytes()).isEqualTo(packet.bytes());
    }

    @Test
    void kod3UzunDolguDaAtilir() throws Exception {
        byte[] padding = new byte[600];
        OpusPacket packet = OpusPacket.parse(TestAudio.paddedPacket(31, 2, 5, padding));
        assertThat(packet.bytes()).hasSize(2 + 10);
    }

    @Test
    void kod3DegiskenBitHizliCercevelerOkunur() throws Exception {
        // VBR, 3 çerçeve: uzunluklar 2 ve 1, sonuncusu kalan (4 bayt).
        byte[] vbr = {(byte) (31 << 3 | 3), (byte) 0x83, 2, 1, 9, 9, 8, 7, 7, 7, 7};
        OpusPacket packet = OpusPacket.parse(vbr);
        assertThat(packet.samples()).isEqualTo(3 * 960);
        assertThat(packet.bytes()).isEqualTo(vbr);

        byte[] vbrOverflow = {(byte) (31 << 3 | 3), (byte) 0x83, 5, 5, 1, 1};
        assertThatThrownBy(() -> OpusPacket.parse(vbrOverflow)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void kod3SifirCerceveYaDa120msUstuReddedilir() {
        byte[] zeroFrames = {(byte) (31 << 3 | 3), 0x00};
        assertThatThrownBy(() -> OpusPacket.parse(zeroFrames)).isInstanceOf(MalformedAudioException.class);
        byte[] tooLong = {(byte) (3 << 3 | 3), 0x03, 1, 1, 1};
        assertThatThrownBy(() -> OpusPacket.parse(tooLong)).isInstanceOf(MalformedAudioException.class);
        byte[] cbrRemainder = {(byte) (31 << 3 | 3), 0x02, 1, 1, 1};
        assertThatThrownBy(() -> OpusPacket.parse(cbrRemainder)).isInstanceOf(MalformedAudioException.class);
        byte[] paddingOverflow = {(byte) (31 << 3 | 3), 0x41, 50, 1};
        assertThatThrownBy(() -> OpusPacket.parse(paddingOverflow)).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void bosPaketVe1275UstuCerceveReddedilir() {
        assertThatThrownBy(() -> OpusPacket.parse(new byte[0])).isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OpusPacket.parse(TestAudio.packet(31, false, 1276)))
                .isInstanceOf(MalformedAudioException.class);
    }
}
