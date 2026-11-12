package com.ltz.messaging.infrastructure.storage.audio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ogg Opus dosyasından (RFC 3533, RFC 7845) Opus başlığını ve ses paketlerini çıkarır (MSG-21). Okuma katıdır: dosya
 * yalnız art arda gelen, CRC'si doğru sayfalardan oluşmalı; tek mantıksal akış olmalı (çoğullanmış ya da zincirlenmiş
 * akış reddedilir); ilk sayfa yalnız {@code OpusHead} taşımalı, ikinci paket {@code OpusTags} olmalı ve atılır. Sayfa
 * dışı ya da son sayfadan sonraki bayt kabul edilmez. Hiçbir girdi baytı denetlenmeden çıktıya geçmez.
 */
public final class OggOpusReader {

    private static final int HEADER_SIZE = 27;
    private static final int FLAG_CONTINUED = 0x01;
    private static final int FLAG_BEGIN = 0x02;
    private static final int FLAG_END = 0x04;
    private static final byte[] CAPTURE = "OggS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TAGS_MAGIC = "OpusTags".getBytes(StandardCharsets.US_ASCII);
    private static final int[] CRC_TABLE = crcTable();

    private OggOpusReader() {
    }

    /**
     * @param maxPackets en çok ses paketi (başlık ve etiket hariç); aşılırsa dosya reddedilir
     */
    public static OpusStream read(byte[] file, int maxPackets) throws MalformedAudioException {
        Packets packets = new Packets(maxPackets);
        ByteArrayOutputStream partial = null;
        int position = 0;
        int expectedSequence = 0;
        int serial = 0;
        boolean ended = false;
        while (position < file.length) {
            if (ended) {
                throw new MalformedAudioException();
            }
            Page page = Page.read(file, position);
            boolean first = expectedSequence == 0;
            page.requireFollows(first, serial, expectedSequence, partial != null);
            serial = page.serial;
            expectedSequence++;
            partial = page.collect(file, partial, packets);
            if (first && (partial != null || packets.count != 1)) {
                // İlk sayfa yalnız kimlik başlığını taşır (RFC 7845 §3).
                throw new MalformedAudioException();
            }
            ended = page.has(FLAG_END);
            position = page.end;
        }
        if (partial != null) {
            throw new MalformedAudioException();
        }
        return packets.toStream();
    }

    /** Paketleri sırasıyla işler: 0 → OpusHead, 1 → OpusTags (atılır), sonrası ses paketi. */
    private static final class Packets {

        private final int maxPackets;
        private final List<OpusPacket> audio = new ArrayList<>();
        private OpusHead head;
        private int count;

        Packets(int maxPackets) {
            this.maxPackets = maxPackets;
        }

        void accept(byte[] packet) throws MalformedAudioException {
            if (count == 0) {
                head = OpusHead.parse(packet);
            } else if (count == 1) {
                if (packet.length < TAGS_MAGIC.length
                        || !Arrays.equals(packet, 0, TAGS_MAGIC.length, TAGS_MAGIC, 0, TAGS_MAGIC.length)) {
                    throw new MalformedAudioException();
                }
            } else {
                if (audio.size() >= maxPackets) {
                    throw new MalformedAudioException();
                }
                audio.add(OpusPacket.parse(packet));
            }
            count++;
        }

        OpusStream toStream() throws MalformedAudioException {
            if (head == null || audio.isEmpty()) {
                throw new MalformedAudioException();
            }
            return new OpusStream(head, audio);
        }
    }

    /** Doğrulanmış sayfa başlığı; gövde dosyada {@code bodyStart..end} aralığıdır. */
    private record Page(int flags, int serial, int sequence, int lacingStart, int segments, int bodyStart, int end) {

        static Page read(byte[] file, int start) throws MalformedAudioException {
            if (file.length - start < HEADER_SIZE
                    || !Arrays.equals(file, start, start + CAPTURE.length, CAPTURE, 0, CAPTURE.length)
                    || file[start + 4] != 0) {
                throw new MalformedAudioException();
            }
            int segments = file[start + 26] & 0xFF;
            int lacingStart = start + HEADER_SIZE;
            int bodyStart = lacingStart + segments;
            if (bodyStart > file.length) {
                throw new MalformedAudioException();
            }
            int bodySize = 0;
            for (int index = lacingStart; index < bodyStart; index++) {
                bodySize += file[index] & 0xFF;
            }
            int end = bodyStart + bodySize;
            if (end > file.length || crc(file, start, end) != readInt(file, start + 22)) {
                throw new MalformedAudioException();
            }
            return new Page(file[start + 5] & 0xFF, readInt(file, start + 14), readInt(file, start + 18), lacingStart,
                    segments, bodyStart, end);
        }

        boolean has(int flag) {
            return (flags & flag) != 0;
        }

        /**
         * Tek akışın sıradaki sayfası mı: yalnız ilk sayfa BOS, seri numarası aynı, sıra numarası boşluksuz, devam bayrağı
         * önceki sayfada yarım kalan paketle tutarlı.
         */
        void requireFollows(boolean first, int expectedSerial, int expectedSequence, boolean continuing)
                throws MalformedAudioException {
            boolean sameStream = first || serial == expectedSerial;
            if (first != has(FLAG_BEGIN) || !sameStream || sequence != expectedSequence
                    || has(FLAG_CONTINUED) != continuing) {
                throw new MalformedAudioException();
            }
        }

        /** Segmentleri pakete ekler; sayfa ortasında biten paketleri işler, yarım kalan paketi döner. */
        ByteArrayOutputStream collect(byte[] file, ByteArrayOutputStream carried, Packets packets)
                throws MalformedAudioException {
            ByteArrayOutputStream current = carried;
            int offset = bodyStart;
            for (int index = 0; index < segments; index++) {
                int length = file[lacingStart + index] & 0xFF;
                if (current == null) {
                    current = new ByteArrayOutputStream();
                }
                current.write(file, offset, length);
                offset += length;
                if (length < 255) {
                    packets.accept(current.toByteArray());
                    current = null;
                }
            }
            return current;
        }
    }

    private static int readInt(byte[] file, int offset) {
        return (file[offset] & 0xFF) | (file[offset + 1] & 0xFF) << 8 | (file[offset + 2] & 0xFF) << 16
                | (file[offset + 3] & 0xFF) << 24;
    }

    /** Ogg CRC-32 (polinom 0x04C11DB7, başlangıç 0, yansıtmasız); CRC alanı (22–25) sıfır sayılır. */
    private static int crc(byte[] file, int start, int end) {
        int crc = 0;
        for (int index = start; index < end; index++) {
            int value = index >= start + 22 && index < start + 26 ? 0 : file[index] & 0xFF;
            crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ value) & 0xFF];
        }
        return crc;
    }

    private static int[] crcTable() {
        int[] table = new int[256];
        for (int index = 0; index < 256; index++) {
            int value = index << 24;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x80000000) != 0 ? (value << 1) ^ 0x04C11DB7 : value << 1;
            }
            table[index] = value;
        }
        return table;
    }
}
