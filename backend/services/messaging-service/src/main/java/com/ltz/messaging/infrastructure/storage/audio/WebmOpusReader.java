package com.ltz.messaging.infrastructure.storage.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.ltz.messaging.infrastructure.storage.audio.Ebml.Header;

/**
 * WebM dosyasından (Matroska alt kümesi) tek Opus izinin başlığını ve paketlerini çıkarır (MSG-21). Tarayıcı
 * {@code MediaRecorder}'ının yazdığı boyutu bilinmeyen {@code Segment} ve {@code Cluster} öğeleri desteklenir.
 * <p>
 * Yalnız sabit yapıya inilir (Segment → Tracks → TrackEntry, Segment → Cluster → SimpleBlock / BlockGroup → Block);
 * tanınmayan öğelerin ({@code Tags}, {@code Void}, {@code Cues}, {@code SeekHead}, {@code Attachments}, …) içine hiç
 * inilmez, atlanır. Bu yüzden iç içelik ne kadar derin olursa olsun özyineleme yoktur. Boyutu bilinmeyen öğe yalnız
 * Segment ve Cluster olabilir. Tek iz olmalı ({@code A_OPUS}); laced blok, Segment dışı ya da Segment'ten sonraki bayt
 * reddedilir. Blok zaman damgaları kullanılmaz; paketler dosya sırasıyla alınır.
 */
public final class WebmOpusReader {

    private static final int AUDIO_TRACK = 2;
    private static final String OPUS = "A_OPUS";
    private static final String WEBM = "webm";
    private static final int LACING_MASK = 0x06;

    private WebmOpusReader() {
    }

    /**
     * @param maxPackets en çok ses paketi; aşılırsa dosya reddedilir
     */
    public static OpusStream read(byte[] file, int maxPackets) throws MalformedAudioException {
        Header ebml = Ebml.readHeader(file, 0, file.length);
        if (ebml.id() != Ebml.EBML || ebml.unknownSize() || !WEBM.equals(docType(file, ebml))) {
            throw new MalformedAudioException();
        }
        Header segment = Ebml.readHeader(file, ebml.end(), file.length);
        if (segment.id() != Ebml.SEGMENT || (!segment.unknownSize() && segment.end() != file.length)) {
            throw new MalformedAudioException();
        }
        State state = new State(maxPackets);
        int position = segment.dataStart();
        while (position < file.length) {
            Header child = Ebml.readHeader(file, position, file.length);
            if (child.id() == Ebml.CLUSTER) {
                position = cluster(file, child, state);
                continue;
            }
            Ebml.requireKnown(child);
            if (child.id() == Ebml.TRACKS) {
                state.tracks(file, child);
            }
            position = child.end();
        }
        return state.toStream();
    }

    private static String docType(byte[] file, Header ebml) throws MalformedAudioException {
        String docType = null;
        int position = ebml.dataStart();
        while (position < ebml.end()) {
            Header child = Ebml.readHeader(file, position, ebml.end());
            if (child.id() == Ebml.DOC_TYPE) {
                docType = Ebml.readString(file, child);
            }
            Ebml.requireKnown(child);
            position = child.end();
        }
        return docType;
    }

    /** Kümenin bloklarını okur; kümeden sonraki konumu döner. Boyutu bilinmeyen küme bir üst düzey öğede biter. */
    private static int cluster(byte[] file, Header cluster, State state) throws MalformedAudioException {
        if (state.head == null) {
            throw new MalformedAudioException();
        }
        int limit = cluster.unknownSize() ? file.length : cluster.end();
        int position = cluster.dataStart();
        while (position < limit) {
            Header child = Ebml.readHeader(file, position, limit);
            if (cluster.unknownSize() && Ebml.isTopLevel(child.id())) {
                return position;
            }
            Ebml.requireKnown(child);
            if (child.id() == Ebml.SIMPLE_BLOCK) {
                state.block(file, child);
            } else if (child.id() == Ebml.BLOCK_GROUP) {
                blockGroup(file, child, state);
            }
            position = child.end();
        }
        return limit;
    }

    private static void blockGroup(byte[] file, Header group, State state) throws MalformedAudioException {
        int position = group.dataStart();
        while (position < group.end()) {
            Header child = Ebml.readHeader(file, position, group.end());
            Ebml.requireKnown(child);
            if (child.id() == Ebml.BLOCK) {
                state.block(file, child);
            }
            position = child.end();
        }
    }

    /** Okunan iz ve paketler. */
    private static final class State {

        private final int maxPackets;
        private final List<OpusPacket> packets = new ArrayList<>();
        private OpusHead head;
        private long trackNumber;

        State(int maxPackets) {
            this.maxPackets = maxPackets;
        }

        void tracks(byte[] file, Header tracks) throws MalformedAudioException {
            if (head != null) {
                throw new MalformedAudioException();
            }
            Header entry = null;
            int position = tracks.dataStart();
            while (position < tracks.end()) {
                Header child = Ebml.readHeader(file, position, tracks.end());
                Ebml.requireKnown(child);
                if (child.id() == Ebml.TRACK_ENTRY) {
                    if (entry != null) {
                        throw new MalformedAudioException();
                    }
                    entry = child;
                }
                position = child.end();
            }
            if (entry == null) {
                throw new MalformedAudioException();
            }
            trackEntry(file, entry);
        }

        private void trackEntry(byte[] file, Header entry) throws MalformedAudioException {
            long number = 0;
            long type = 0;
            String codec = null;
            OpusHead parsed = null;
            int position = entry.dataStart();
            while (position < entry.end()) {
                Header child = Ebml.readHeader(file, position, entry.end());
                Ebml.requireKnown(child);
                switch (child.id()) {
                    case Ebml.TRACK_NUMBER -> number = Ebml.readUnsigned(file, child);
                    case Ebml.TRACK_TYPE -> type = Ebml.readUnsigned(file, child);
                    case Ebml.CODEC_ID -> codec = Ebml.readString(file, child);
                    case Ebml.CODEC_PRIVATE -> parsed = OpusHead.parse(
                            Arrays.copyOfRange(file, child.dataStart(), child.end()));
                    default -> {
                        // Diğer iz alanları kullanılmaz ve çıktıya geçmez.
                    }
                }
                position = child.end();
            }
            if (number < 1 || type != AUDIO_TRACK || !OPUS.equals(codec) || parsed == null) {
                throw new MalformedAudioException();
            }
            this.trackNumber = number;
            this.head = parsed;
        }

        /** Block / SimpleBlock: iz numarası (vint), 16 bit zaman, bayraklar, lacing yoksa tek çerçeve. */
        void block(byte[] file, Header block) throws MalformedAudioException {
            int position = block.dataStart();
            int end = block.end();
            if (position >= end) {
                throw new MalformedAudioException();
            }
            int first = file[position] & 0xFF;
            int length = Integer.numberOfLeadingZeros(first) - 23;
            if (first == 0 || length > 8 || position + length + 3 > end) {
                throw new MalformedAudioException();
            }
            long track = first & (0xFF >>> length);
            for (int index = 1; index < length; index++) {
                track = track << 8 | (file[position + index] & 0xFF);
            }
            int flags = file[position + length + 2] & 0xFF;
            if (track != trackNumber || (flags & LACING_MASK) != 0 || packets.size() >= maxPackets) {
                throw new MalformedAudioException();
            }
            packets.add(OpusPacket.parse(Arrays.copyOfRange(file, position + length + 3, end)));
        }

        OpusStream toStream() throws MalformedAudioException {
            if (head == null || packets.isEmpty()) {
                throw new MalformedAudioException();
            }
            return new OpusStream(head, packets);
        }
    }
}
