package com.ltz.messaging.infrastructure.storage.audio;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Opus sesini sıfırdan WebM olarak yazar (MSG-21). Çıktıda yalnız oynatma için gerekenler vardır: EBML başlığı, süresi
 * dolu {@code Info}, tek Opus izi (başlık {@link OpusHead#toBytes()} ile kurulur), kümelerin konumunu gösteren
 * {@code Cues} ve paketleri taşıyan {@code SimpleBlock}'lar. Zaman damgaları paket sürelerinden hesaplanır; kümeler en
 * çok 5 saniyedir (bloğun 16 bitlik göreli zaman damgasına rahatça sığar). {@code Cues} kümelerden önce yazılır ve
 * küme konumları sabit 8 baytlık alanlarla tutulur; böylece tarayıcı ileri sararken kümeleri bulur.
 */
public final class WebmOpusWriter {

    static final int CLUSTER_SPAN_MILLIS = 5000;

    private static final int DOC_TYPE_VERSION = 4;
    private static final int DOC_TYPE_READ_VERSION = 2;
    private static final long TIMESTAMP_SCALE_NANOS = 1_000_000;
    private static final long SEEK_PRE_ROLL_NANOS = 80_000_000;
    private static final int TRACK_NUMBER = 1;
    private static final int AUDIO_TRACK = 2;
    private static final int KEYFRAME = 0x80;
    private static final int POSITION_BYTES = 8;
    private static final String APP = "ltz-messaging";

    private static final int EBML_VERSION = 0x4286;
    private static final int EBML_READ_VERSION = 0x42F7;
    private static final int EBML_MAX_ID_LENGTH = 0x42F2;
    private static final int EBML_MAX_SIZE_LENGTH = 0x42F3;
    private static final int DOC_TYPE_VERSION_ID = 0x4287;
    private static final int DOC_TYPE_READ_VERSION_ID = 0x4285;
    private static final int TIMESTAMP_SCALE = 0x2AD7B1;
    private static final int DURATION = 0x4489;
    private static final int MUXING_APP = 0x4D80;
    private static final int WRITING_APP = 0x5741;
    private static final int TRACK_UID = 0x73C5;
    private static final int CODEC_DELAY = 0x56AA;
    private static final int SEEK_PRE_ROLL = 0x56BB;
    private static final int AUDIO = 0xE1;
    private static final int SAMPLING_FREQUENCY = 0xB5;
    private static final int CHANNELS = 0x9F;
    private static final int CUE_POINT = 0xBB;
    private static final int CUE_TIME = 0xB3;
    private static final int CUE_TRACK_POSITIONS = 0xB7;
    private static final int CUE_TRACK = 0xF7;
    private static final int CUE_CLUSTER_POSITION = 0xF1;

    private WebmOpusWriter() {
    }

    public static byte[] write(OpusStream stream) {
        byte[] info = info(stream);
        byte[] tracks = tracks(stream.head());
        List<Cluster> clusters = clusters(stream.packets());
        // Cues'un boyutu konumlardan bağımsızdır (sabit genişlik); önce boyut, sonra gerçek konumlar.
        int cuesSize = cues(clusters, 0).length;
        byte[] cues = cues(clusters, info.length + tracks.length + cuesSize);

        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        segment.writeBytes(info);
        segment.writeBytes(tracks);
        segment.writeBytes(cues);
        for (Cluster cluster : clusters) {
            segment.writeBytes(cluster.bytes());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(segment.size() + 64);
        out.writeBytes(ebmlHeader());
        Ebml.writeElement(out, Ebml.SEGMENT, segment.toByteArray());
        return out.toByteArray();
    }

    private static byte[] ebmlHeader() {
        return Ebml.element(Ebml.EBML,
                Ebml.unsigned(EBML_VERSION, 1),
                Ebml.unsigned(EBML_READ_VERSION, 1),
                Ebml.unsigned(EBML_MAX_ID_LENGTH, 4),
                Ebml.unsigned(EBML_MAX_SIZE_LENGTH, 8),
                Ebml.string(Ebml.DOC_TYPE, "webm"),
                Ebml.unsigned(DOC_TYPE_VERSION_ID, DOC_TYPE_VERSION),
                Ebml.unsigned(DOC_TYPE_READ_VERSION_ID, DOC_TYPE_READ_VERSION));
    }

    private static byte[] info(OpusStream stream) {
        return Ebml.element(Ebml.INFO,
                Ebml.unsigned(TIMESTAMP_SCALE, TIMESTAMP_SCALE_NANOS),
                Ebml.float64(DURATION, stream.durationMillis()),
                Ebml.string(MUXING_APP, APP),
                Ebml.string(WRITING_APP, APP));
    }

    private static byte[] tracks(OpusHead head) {
        long codecDelay = head.preSkip() * 1_000_000_000L / OpusHead.SAMPLE_RATE;
        return Ebml.element(Ebml.TRACKS, Ebml.element(Ebml.TRACK_ENTRY,
                Ebml.unsigned(Ebml.TRACK_NUMBER, TRACK_NUMBER),
                Ebml.unsigned(TRACK_UID, 1),
                Ebml.unsigned(Ebml.TRACK_TYPE, AUDIO_TRACK),
                Ebml.string(Ebml.CODEC_ID, "A_OPUS"),
                Ebml.element(Ebml.CODEC_PRIVATE, head.toBytes()),
                Ebml.unsigned(CODEC_DELAY, codecDelay),
                Ebml.unsigned(SEEK_PRE_ROLL, SEEK_PRE_ROLL_NANOS),
                Ebml.element(AUDIO,
                        Ebml.float64(SAMPLING_FREQUENCY, OpusHead.SAMPLE_RATE),
                        Ebml.unsigned(CHANNELS, head.channels()))));
    }

    /** Paketleri en çok {@link #CLUSTER_SPAN_MILLIS} süren kümelere böler. */
    private static List<Cluster> clusters(List<OpusPacket> packets) {
        List<Cluster> clusters = new ArrayList<>();
        ByteArrayOutputStream blocks = new ByteArrayOutputStream();
        long samples = 0;
        long clusterStart = 0;
        for (OpusPacket packet : packets) {
            long timestamp = samples * 1000 / OpusHead.SAMPLE_RATE;
            if (timestamp - clusterStart >= CLUSTER_SPAN_MILLIS) {
                clusters.add(Cluster.of(clusterStart, blocks.toByteArray()));
                blocks.reset();
                clusterStart = timestamp;
            }
            int relative = (int) (timestamp - clusterStart);
            Ebml.writeHeader(blocks, Ebml.SIMPLE_BLOCK, 4L + packet.length());
            blocks.write(0x80 | TRACK_NUMBER);
            blocks.write(relative >>> 8);
            blocks.write(relative);
            blocks.write(KEYFRAME);
            packet.writeTo(blocks);
            samples += packet.samples();
        }
        clusters.add(Cluster.of(clusterStart, blocks.toByteArray()));
        return clusters;
    }

    /** Her küme için bir CuePoint; konum, Segment verisinin başından {@code firstClusterOffset} + önceki kümeler. */
    private static byte[] cues(List<Cluster> clusters, long firstClusterOffset) {
        ByteArrayOutputStream points = new ByteArrayOutputStream();
        long position = firstClusterOffset;
        for (Cluster cluster : clusters) {
            points.writeBytes(Ebml.element(CUE_POINT,
                    Ebml.fixedUnsigned(CUE_TIME, cluster.timestamp(), POSITION_BYTES),
                    Ebml.element(CUE_TRACK_POSITIONS,
                            Ebml.unsigned(CUE_TRACK, TRACK_NUMBER),
                            Ebml.fixedUnsigned(CUE_CLUSTER_POSITION, position, POSITION_BYTES))));
            position += cluster.bytes().length;
        }
        return Ebml.element(Ebml.CUES, points.toByteArray());
    }

    /** Yazılmış küme ve mutlak zaman damgası (ms). */
    private record Cluster(long timestamp, byte[] bytes) {

        static Cluster of(long timestamp, byte[] blocks) {
            return new Cluster(timestamp, Ebml.element(Ebml.CLUSTER, Ebml.unsigned(Ebml.TIMESTAMP, timestamp),
                    blocks));
        }
    }
}
