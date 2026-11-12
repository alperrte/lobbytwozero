package com.ltz.messaging.infrastructure.storage.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.TestAudio;
import com.ltz.messaging.infrastructure.storage.audio.Ebml.Header;

/** Sıfırdan yazılan WebM: aynı paketler, dolu süre, 16 bit sınırına sığan kümeler, kümeleri gösteren Cues. */
class WebmOpusWriterTest {

    private static final int DURATION = 0x4489;
    private static final int CUE_POINT = 0xBB;
    private static final int CUE_TIME = 0xB3;
    private static final int CUE_TRACK_POSITIONS = 0xB7;
    private static final int CUE_CLUSTER_POSITION = 0xF1;

    @Test
    void yazilanDosyaYenidenOkununcaAyniPaketlerVeSure() throws Exception {
        OpusStream source = stream(new OpusHead(2, 312), 6000);

        byte[] file = WebmOpusWriter.write(source);
        OpusStream reread = WebmOpusReader.read(file, 48_400);

        assertThat(reread.head()).isEqualTo(source.head());
        assertThat(reread.packets()).isEqualTo(source.packets());
        Segment segment = Segment.of(file);
        assertThat(segment.durationMillis).isEqualTo(source.durationMillis());
        assertThat(segment.clusterTimestamps).hasSize(24).first().isEqualTo(0L);
        assertThat(segment.maxRelativeTimestamp).isLessThanOrEqualTo(5000);
        assertThat(segment.codecPrivateMatches).isTrue();
    }

    @Test
    void cuesKumeKonumlariniGosterir() throws Exception {
        byte[] file = WebmOpusWriter.write(stream(new OpusHead(1, 312), 1000));
        Segment segment = Segment.of(file);

        assertThat(segment.cuePositions).hasSize(segment.clusterTimestamps.size());
        for (int index = 0; index < segment.cuePositions.size(); index++) {
            Header atCue = Ebml.readHeader(file, segment.dataStart + segment.cuePositions.get(index).intValue(),
                    file.length);
            assertThat(atCue.id()).isEqualTo(Ebml.CLUSTER);
            assertThat(segment.cueTimes.get(index)).isEqualTo(segment.clusterTimestamps.get(index));
        }
    }

    @Test
    void tekPaketlikSesYazilir() throws Exception {
        byte[] file = WebmOpusWriter.write(stream(new OpusHead(1, 0), 1));
        assertThat(WebmOpusReader.read(file, 10).packets()).hasSize(1);
        assertThat(Segment.of(file).durationMillis).isEqualTo(20);
    }

    private static OpusStream stream(OpusHead head, int count) throws MalformedAudioException {
        List<OpusPacket> packets = new ArrayList<>();
        for (byte[] raw : TestAudio.packets(count)) {
            packets.add(OpusPacket.parse(raw));
        }
        return new OpusStream(head, packets);
    }

    /** Yazılan dosyanın denetim için okunan alanları. */
    private static final class Segment {

        private int dataStart;
        private long durationMillis;
        private boolean codecPrivateMatches;
        private int maxRelativeTimestamp;
        private final List<Long> clusterTimestamps = new ArrayList<>();
        private final List<Long> cuePositions = new ArrayList<>();
        private final List<Long> cueTimes = new ArrayList<>();

        static Segment of(byte[] file) throws MalformedAudioException {
            Segment result = new Segment();
            Header ebml = Ebml.readHeader(file, 0, file.length);
            Header segment = Ebml.readHeader(file, ebml.end(), file.length);
            assertThat(segment.unknownSize()).isFalse();
            assertThat(segment.end()).isEqualTo(file.length);
            result.dataStart = segment.dataStart();
            for (Header child : children(file, segment)) {
                switch (child.id()) {
                    case Ebml.INFO -> result.info(file, child);
                    case Ebml.TRACKS -> result.tracks(file, child);
                    case Ebml.CUES -> result.cues(file, child);
                    case Ebml.CLUSTER -> result.cluster(file, child);
                    default -> throw new AssertionError("Beklenmeyen öğe: " + Integer.toHexString(child.id()));
                }
            }
            return result;
        }

        private void info(byte[] file, Header info) throws MalformedAudioException {
            for (Header child : children(file, info)) {
                if (child.id() == DURATION) {
                    durationMillis = (long) Double.longBitsToDouble(Ebml.readUnsigned(file, child));
                }
            }
        }

        private void tracks(byte[] file, Header tracks) throws MalformedAudioException {
            Header entry = children(file, tracks).getFirst();
            for (Header child : children(file, entry)) {
                if (child.id() == Ebml.CODEC_PRIVATE) {
                    byte[] head = java.util.Arrays.copyOfRange(file, child.dataStart(), child.end());
                    codecPrivateMatches = java.util.Arrays.equals(head, OpusHead.parse(head).toBytes());
                }
            }
        }

        private void cues(byte[] file, Header cues) throws MalformedAudioException {
            for (Header point : children(file, cues)) {
                assertThat(point.id()).isEqualTo(CUE_POINT);
                for (Header child : children(file, point)) {
                    if (child.id() == CUE_TIME) {
                        cueTimes.add(Ebml.readUnsigned(file, child));
                    } else if (child.id() == CUE_TRACK_POSITIONS) {
                        for (Header position : children(file, child)) {
                            if (position.id() == CUE_CLUSTER_POSITION) {
                                cuePositions.add(Ebml.readUnsigned(file, position));
                            }
                        }
                    }
                }
            }
        }

        private void cluster(byte[] file, Header cluster) throws MalformedAudioException {
            for (Header child : children(file, cluster)) {
                if (child.id() == Ebml.TIMESTAMP) {
                    clusterTimestamps.add(Ebml.readUnsigned(file, child));
                } else if (child.id() == Ebml.SIMPLE_BLOCK) {
                    int relative = (short) ((file[child.dataStart() + 1] & 0xFF) << 8
                            | file[child.dataStart() + 2] & 0xFF);
                    assertThat(relative).isNotNegative();
                    maxRelativeTimestamp = Math.max(maxRelativeTimestamp, relative);
                }
            }
        }

        private static List<Header> children(byte[] file, Header parent) throws MalformedAudioException {
            List<Header> children = new ArrayList<>();
            int position = parent.dataStart();
            while (position < parent.end()) {
                Header child = Ebml.readHeader(file, position, parent.end());
                children.add(child);
                position = child.end();
            }
            return children;
        }
    }
}
