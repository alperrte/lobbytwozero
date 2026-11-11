package com.ltz.messaging;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Testler için kodda üretilen sentetik Opus sesleri (MSG-21). Paketler RFC 6716'ya göre yapısal olarak geçerlidir ama
 * çalınabilir ses değildir; ayrıştırıcı sesi çözmediği için yeterlidir. İkili test dosyası tutulmaz.
 */
public final class TestAudio {

    /** CELT tam bant 20 ms (960 örnek). */
    public static final int CONFIG_CELT_20MS = 31;
    public static final int SAMPLES_20MS = 960;
    public static final int FRAME_BYTES = 40;

    private TestAudio() {
    }

    /** Kod 0 paketi: tek çerçeve. */
    public static byte[] packet(int config, boolean stereo, int frameBytes) {
        byte[] packet = new byte[1 + frameBytes];
        packet[0] = toc(config, stereo, 0);
        fillFrame(packet, 1, frameBytes, 0x55);
        return packet;
    }

    /** Kod 3, sabit bit hızlı, dolgulu paket: {@code frames} çerçeve ve sonda {@code padding} baytları. */
    public static byte[] paddedPacket(int config, int frames, int frameBytes, byte[] padding) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(toc(config, false, 3));
        out.write(0x40 | frames);
        int remaining = padding.length;
        while (remaining >= 255) {
            out.write(255);
            remaining -= 254;
        }
        out.write(remaining);
        byte[] frameData = new byte[frames * frameBytes];
        fillFrame(frameData, 0, frameData.length, 0x33);
        out.writeBytes(frameData);
        out.writeBytes(padding);
        return out.toByteArray();
    }

    /** {@code count} adet 20 ms'lik kod 0 paketi. */
    public static List<byte[]> packets(int count) {
        List<byte[]> packets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            packets.add(packet(CONFIG_CELT_20MS, false, FRAME_BYTES));
        }
        return packets;
    }

    /** Geçerli {@code OpusHead}: sürüm 1, giriş hızı 48000, kazanç 0, eşleme ailesi 0. */
    public static byte[] head(int channels, int preSkip) {
        return head(1, channels, preSkip, 48_000, 0, 0);
    }

    public static byte[] head(int version, int channels, int preSkip, int inputRate, int gain, int family) {
        byte[] head = new byte[19];
        System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, head, 0, 8);
        head[8] = (byte) version;
        head[9] = (byte) channels;
        head[10] = (byte) preSkip;
        head[11] = (byte) (preSkip >>> 8);
        for (int index = 0; index < 4; index++) {
            head[12 + index] = (byte) (inputRate >>> (8 * index));
        }
        head[16] = (byte) gain;
        head[17] = (byte) (gain >>> 8);
        head[18] = (byte) family;
        return head;
    }

    /** {@code OpusTags}: satıcı dizgisi ve yorumlar. */
    public static byte[] tags(String... comments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("OpusTags".getBytes(StandardCharsets.US_ASCII));
        writeLengthPrefixed(out, "test-vendor");
        writeIntLe(out, comments.length);
        for (String comment : comments) {
            writeLengthPrefixed(out, comment);
        }
        return out.toByteArray();
    }

    /** {@code haystack} içinde {@code needle} bayt dizisi geçiyor mu? */
    public static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start <= haystack.length - needle.length; start++) {
            for (int index = 0; index < needle.length; index++) {
                if (haystack[start + index] != needle[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    /** Varsayılan geçerli Ogg Opus dosyası: mono, pre-skip 312, {@code count} × 20 ms. */
    public static byte[] ogg(int count) {
        return new Ogg(head(1, 312), tags(), packets(count)).build();
    }

    /** Sınanacak bozukluklarıyla birlikte Ogg Opus yazıcısı (üretim kodundan bağımsız). */
    public static final class Ogg {

        private final byte[] head;
        private final byte[] tags;
        private final List<byte[]> packets;
        private int serial = 0x4C545A31;
        private int packetsPerPage = 50;
        private int tagsSegmentsPerPage = 255;
        private boolean extraStream;
        private boolean corruptCrc;
        private boolean endOfStream = true;
        private byte[] trailing = new byte[0];

        public Ogg(byte[] head, byte[] tags, List<byte[]> packets) {
            this.head = head;
            this.tags = tags;
            this.packets = packets;
        }

        /** {@code OpusTags} paketini sayfa başına bu kadar segmentle böler (1 → her 255 bayt yeni sayfa). */
        public Ogg tagsSegmentsPerPage(int value) {
            this.tagsSegmentsPerPage = value;
            return this;
        }

        public Ogg packetsPerPage(int value) {
            this.packetsPerPage = value;
            return this;
        }

        /** Başlık sayfasından sonra farklı seri numaralı ikinci bir akışın BOS sayfası. */
        public Ogg extraStream() {
            this.extraStream = true;
            return this;
        }

        public Ogg corruptCrc() {
            this.corruptCrc = true;
            return this;
        }

        public Ogg withoutEndOfStream() {
            this.endOfStream = false;
            return this;
        }

        public Ogg trailing(byte[] value) {
            this.trailing = value;
            return this;
        }

        public byte[] build() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int[] sequence = {0};
            writePage(out, 0x02, 0, serial, sequence[0]++, List.of(head));
            if (extraStream) {
                writePage(out, 0x02, 0, serial + 1, 0, List.of(head));
            }
            writeSplit(out, tags, sequence);
            for (int start = 0; start < packets.size(); start += packetsPerPage) {
                List<byte[]> page = packets.subList(start, Math.min(packets.size(), start + packetsPerPage));
                boolean last = start + packetsPerPage >= packets.size();
                writePage(out, last && endOfStream ? 0x04 : 0, (long) (start + page.size()) * SAMPLES_20MS, serial,
                        sequence[0]++, page);
            }
            byte[] file = concat(out.toByteArray(), trailing);
            if (corruptCrc) {
                file[22] ^= 0x01;
            }
            return file;
        }

        /** Tek paketi segment sınırında birden çok sayfaya böler. */
        private void writeSplit(ByteArrayOutputStream out, byte[] packet, int[] sequence) {
            int chunk = tagsSegmentsPerPage * 255;
            boolean continued = false;
            for (int offset = 0; ; offset += chunk) {
                int remaining = packet.length - offset;
                if (remaining >= chunk) {
                    // Paket bu sayfada bitmiyor: yalnız 255'lik segmentler, sonraki sayfa devam bayrağı taşır.
                    writeRawPage(out, continued ? 0x01 : 0, -1, serial, sequence[0]++,
                            Arrays.copyOfRange(packet, offset, offset + chunk), tagsSegmentsPerPage, false);
                    continued = true;
                    if (remaining == chunk) {
                        writeRawPage(out, 0x01, 0, serial, sequence[0]++, new byte[0], 0, true);
                        return;
                    }
                } else {
                    writeRawPage(out, continued ? 0x01 : 0, 0, serial, sequence[0]++,
                            Arrays.copyOfRange(packet, offset, packet.length), -1, true);
                    return;
                }
            }
        }

        private static void writePage(ByteArrayOutputStream out, int flags, long granule, int serial, int sequence,
                                      List<byte[]> packets) {
            ByteArrayOutputStream lacing = new ByteArrayOutputStream();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            for (byte[] packet : packets) {
                int remaining = packet.length;
                while (remaining >= 255) {
                    lacing.write(255);
                    remaining -= 255;
                }
                lacing.write(remaining);
                body.writeBytes(packet);
            }
            emit(out, flags, granule, serial, sequence, lacing.toByteArray(), body.toByteArray());
        }

        /** {@code segments} ≥ 0 ise tam o kadar 255'lik segment (paket bitmez); aksi halde paketi bitiren lacing. */
        private static void writeRawPage(ByteArrayOutputStream out, int flags, long granule, int serial, int sequence,
                                         byte[] data, int segments, boolean ends) {
            ByteArrayOutputStream lacing = new ByteArrayOutputStream();
            if (!ends) {
                for (int index = 0; index < segments; index++) {
                    lacing.write(255);
                }
            } else {
                int remaining = data.length;
                while (remaining >= 255) {
                    lacing.write(255);
                    remaining -= 255;
                }
                lacing.write(remaining);
            }
            emit(out, flags, granule, serial, sequence, lacing.toByteArray(), data);
        }

        private static void emit(ByteArrayOutputStream out, int flags, long granule, int serial, int sequence,
                                 byte[] lacing, byte[] body) {
            byte[] page = new byte[27 + lacing.length + body.length];
            page[0] = 'O';
            page[1] = 'g';
            page[2] = 'g';
            page[3] = 'S';
            page[5] = (byte) flags;
            for (int index = 0; index < 8; index++) {
                page[6 + index] = (byte) (granule >>> (8 * index));
            }
            for (int index = 0; index < 4; index++) {
                page[14 + index] = (byte) (serial >>> (8 * index));
                page[18 + index] = (byte) (sequence >>> (8 * index));
            }
            page[26] = (byte) lacing.length;
            System.arraycopy(lacing, 0, page, 27, lacing.length);
            System.arraycopy(body, 0, page, 27 + lacing.length, body.length);
            int crc = oggCrc(page);
            for (int index = 0; index < 4; index++) {
                page[22 + index] = (byte) (crc >>> (8 * index));
            }
            out.writeBytes(page);
        }
    }

    /** Varsayılan geçerli WebM Opus dosyası (bilinen boyutlar): mono, pre-skip 312, {@code count} × 20 ms. */
    public static byte[] webm(int count) {
        return new Webm(head(1, 312), packets(count)).build();
    }

    /** Sınanacak biçimleriyle birlikte WebM Opus yazıcısı (üretim kodundan bağımsız EBML kodlayıcı). */
    public static final class Webm {

        private final byte[] head;
        private final List<byte[]> packets;
        private boolean unknownSizes;
        private String docType = "webm";
        private String codecId = "A_OPUS";
        private byte[] tagsPayload;
        private byte[] voidPayload;
        private byte[] seekHeadPayload;
        private boolean extraVideoTrack;
        private boolean laced;
        private boolean blockGroups;
        private int packetsPerCluster = 40;
        private byte[] trailing = new byte[0];

        public Webm(byte[] head, List<byte[]> packets) {
            this.head = head;
            this.packets = packets;
        }

        /** Chrome MediaRecorder biçimi: boyutu bilinmeyen Segment ve Cluster'lar, Duration yok. */
        public Webm unknownSizes() {
            this.unknownSizes = true;
            return this;
        }

        public Webm docType(String value) {
            this.docType = value;
            return this;
        }

        public Webm codecId(String value) {
            this.codecId = value;
            return this;
        }

        public Webm tags(byte[] payload) {
            this.tagsPayload = payload;
            return this;
        }

        public Webm voidElement(byte[] payload) {
            this.voidPayload = payload;
            return this;
        }

        public Webm seekHead(byte[] payload) {
            this.seekHeadPayload = payload;
            return this;
        }

        public Webm extraVideoTrack() {
            this.extraVideoTrack = true;
            return this;
        }

        public Webm laced() {
            this.laced = true;
            return this;
        }

        public Webm blockGroups() {
            this.blockGroups = true;
            return this;
        }

        public Webm packetsPerCluster(int value) {
            this.packetsPerCluster = value;
            return this;
        }

        public Webm trailing(byte[] value) {
            this.trailing = value;
            return this;
        }

        public byte[] build() {
            ByteArrayOutputStream segment = new ByteArrayOutputStream();
            if (seekHeadPayload != null) {
                segment.writeBytes(element(0x114D9B74, seekHeadPayload));
            }
            ByteArrayOutputStream info = new ByteArrayOutputStream();
            info.writeBytes(element(0x2AD7B1, uint(1_000_000)));
            info.writeBytes(element(0x4D80, ascii("test-muxer")));
            segment.writeBytes(element(0x1549A966, info.toByteArray()));
            segment.writeBytes(element(0x1654AE6B, tracks()));
            if (voidPayload != null) {
                segment.writeBytes(element(0xEC, voidPayload));
            }
            for (int start = 0; start < packets.size(); start += packetsPerCluster) {
                segment.writeBytes(cluster(start, Math.min(packets.size(), start + packetsPerCluster)));
            }
            if (tagsPayload != null) {
                segment.writeBytes(element(0x1254C367, tagsPayload));
            }
            byte[] segmentBytes = unknownSizes
                    ? unknownSize(0x18538067, segment.toByteArray())
                    : element(0x18538067, segment.toByteArray());
            return concat(ebmlHeader(docType), segmentBytes, trailing);
        }

        private byte[] tracks() {
            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            audio.writeBytes(element(0xD7, uint(1)));
            audio.writeBytes(element(0x73C5, uint(42)));
            audio.writeBytes(element(0x83, uint(2)));
            audio.writeBytes(element(0x86, ascii(codecId)));
            audio.writeBytes(element(0x63A2, head));
            audio.writeBytes(element(0xE1, concat(element(0xB5, float64(48_000)), element(0x9F, uint(1)))));
            byte[] tracks = element(0xAE, audio.toByteArray());
            if (extraVideoTrack) {
                ByteArrayOutputStream video = new ByteArrayOutputStream();
                video.writeBytes(element(0xD7, uint(2)));
                video.writeBytes(element(0x83, uint(1)));
                video.writeBytes(element(0x86, ascii("V_VP8")));
                tracks = concat(tracks, element(0xAE, video.toByteArray()));
            }
            return tracks;
        }

        private byte[] cluster(int from, int to) {
            ByteArrayOutputStream cluster = new ByteArrayOutputStream();
            cluster.writeBytes(element(0xE7, uint((long) from * 20)));
            for (int index = from; index < to; index++) {
                byte[] block = block((index - from) * 20, packets.get(index));
                if (blockGroups) {
                    cluster.writeBytes(element(0xA0, element(0xA1, block)));
                } else {
                    cluster.writeBytes(element(0xA3, block));
                }
            }
            return unknownSizes ? unknownSize(0x1F43B675, cluster.toByteArray())
                    : element(0x1F43B675, cluster.toByteArray());
        }

        private byte[] block(int relative, byte[] packet) {
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            block.write(0x81);
            block.write(relative >>> 8);
            block.write(relative);
            block.write(laced ? 0x80 | 0x02 : 0x80);
            if (laced) {
                block.write(0);
            }
            block.writeBytes(packet);
            return block.toByteArray();
        }
    }

    public static byte[] ebmlHeader(String docType) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.writeBytes(element(0x4286, uint(1)));
        header.writeBytes(element(0x42F7, uint(1)));
        header.writeBytes(element(0x42F2, uint(4)));
        header.writeBytes(element(0x42F3, uint(8)));
        header.writeBytes(element(0x4282, ascii(docType)));
        header.writeBytes(element(0x4287, uint(4)));
        header.writeBytes(element(0x4285, uint(2)));
        return element(0x1A45DFA3, header.toByteArray());
    }

    /** Bilinen boyutlu EBML öğesi (boyut 8 baytlık vint). */
    public static byte[] element(int id, byte[] payload) {
        return concat(id(id), size(payload.length), payload);
    }

    /** Boyutu bilinmeyen EBML öğesi (8 baytlık "tüm bitler 1"). */
    public static byte[] unknownSize(int id, byte[] payload) {
        return concat(id(id), new byte[] {0x01, -1, -1, -1, -1, -1, -1, -1}, payload);
    }

    /** Öğe başlığı: kimlik + istenen boyut (gövde yazılmaz). */
    public static byte[] elementHeader(int id, long size) {
        return concat(id(id), size(size));
    }

    public static byte[] id(int id) {
        int length = id > 0xFFFFFF ? 4 : id > 0xFFFF ? 3 : id > 0xFF ? 2 : 1;
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = (byte) (id >>> (8 * (length - 1 - index)));
        }
        return bytes;
    }

    public static byte[] size(long size) {
        byte[] bytes = new byte[8];
        bytes[0] = 0x01;
        for (int index = 1; index < 8; index++) {
            bytes[index] = (byte) (size >>> (8 * (7 - index)));
        }
        return bytes;
    }

    public static byte[] uint(long value) {
        byte[] bytes = new byte[8];
        for (int index = 0; index < 8; index++) {
            bytes[index] = (byte) (value >>> (8 * (7 - index)));
        }
        return bytes;
    }

    public static byte[] float64(double value) {
        return uint(Double.doubleToLongBits(value));
    }

    public static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /** Ogg CRC-32: polinom 0x04C11DB7, başlangıç 0, yansıtmasız (CRC alanı sıfırken hesaplanır). */
    public static int oggCrc(byte[] page) {
        int crc = 0;
        for (byte value : page) {
            crc ^= (value & 0xFF) << 24;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04C11DB7 : crc << 1;
            }
        }
        return crc;
    }

    static byte toc(int config, boolean stereo, int code) {
        return (byte) ((config << 3) | (stereo ? 0x04 : 0) | code);
    }

    private static void fillFrame(byte[] target, int offset, int length, int seed) {
        Arrays.fill(target, offset, offset + length, (byte) seed);
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeIntLe(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeIntLe(ByteArrayOutputStream out, int value) {
        for (int index = 0; index < 4; index++) {
            out.write(value >>> (8 * index));
        }
    }
}
