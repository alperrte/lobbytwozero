package com.ltz.messaging.infrastructure.storage.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;

/** {@code OpusHead} (RFC 7845 §5.1): yalnız kanal ve pre-skip alınır, başlık sıfırdan kurulur. */
class OpusHeadTest {

    @Test
    void opusHeadDogrulanirVeYenidenKurulur() throws Exception {
        OpusHead head = OpusHead.parse(TestAudio.head(2, 312));
        assertThat(head.channels()).isEqualTo(2);
        assertThat(head.preSkip()).isEqualTo(312);

        byte[] rebuilt = head.toBytes();
        assertThat(rebuilt).isEqualTo(TestAudio.head(2, 312));
        assertThat(OpusHead.parse(rebuilt)).isEqualTo(head);
    }

    @Test
    void kazancVeGirisHiziSifirdanYazilir() throws Exception {
        OpusHead head = OpusHead.parse(TestAudio.head(1, 1, 3840, 44_100, 1536, 0));
        assertThat(head.toBytes()).isEqualTo(TestAudio.head(1, 3840));
    }

    @Test
    void opusHeadKanalSurumVeAileKurallari() {
        assertThatThrownBy(() -> OpusHead.parse(TestAudio.head(0, 312))).isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OpusHead.parse(TestAudio.head(3, 312))).isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OpusHead.parse(TestAudio.head(0x10, 1, 312, 48_000, 0, 0)))
                .isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OpusHead.parse(TestAudio.head(1, 2, 312, 48_000, 0, 1)))
                .isInstanceOf(MalformedAudioException.class);
        byte[] badMagic = TestAudio.head(1, 312);
        badMagic[0] = 'X';
        assertThatThrownBy(() -> OpusHead.parse(badMagic)).isInstanceOf(MalformedAudioException.class);
        assertThatThrownBy(() -> OpusHead.parse(new byte[18])).isInstanceOf(MalformedAudioException.class);
    }

    @Test
    void akisSuresiPreSkipDusulerekHesaplanir() throws Exception {
        List<OpusPacket> packets = TestAudio.packets(50).stream().map(OpusHeadTest::parse).toList();
        OpusStream stream = new OpusStream(new OpusHead(1, 312), packets);
        assertThat(stream.totalSamples()).isEqualTo(50L * 960);
        assertThat(stream.durationMillis()).isEqualTo((50L * 960 - 312) / 48);
        assertThat(new OpusStream(new OpusHead(1, 5000), packets.subList(0, 1)).durationMillis()).isZero();
    }

    private static OpusPacket parse(byte[] raw) {
        try {
            return OpusPacket.parse(raw);
        } catch (MalformedAudioException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
