package com.ltz.messaging.infrastructure.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * GIF dosyasını çözmeden blok blok yürür ve yalnız görüntü için gereken blokları yeni bir çıktıya yazar: başlık, ekran
 * tanımı, renk tabloları, kareler, kare süresi/saydamlık bloğu (0xF9) ve döngü bloğu ({@code NETSCAPE2.0}). Yorum, düz
 * metin ve diğer uygulama blokları ile kapanış işaretinden sonraki her bayt atılır; böylece dosyada gizli veri (polyglot
 * yük, metadata) kalmaz. Yapısı bozuk, kesik ya da kare sayısı sınırı aşan dosya {@link MalformedGifException} ile
 * reddedilir. Hiçbir okuma dizi sınırının dışına çıkmaz.
 */
final class GifCleaner {

    private static final int EXTENSION = 0x21;
    private static final int IMAGE = 0x2C;
    private static final int TRAILER = 0x3B;
    private static final int GRAPHIC_CONTROL = 0xF9;
    private static final int APPLICATION = 0xFF;
    private static final int HEADER_SIZE = 6;
    private static final int SCREEN_SIZE = 7;
    private static final int IMAGE_DESCRIPTOR_SIZE = 9;
    private static final byte[] GIF89A = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF87A = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LOOP_ID = "NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII);

    private final byte[] source;
    private final ByteArrayOutputStream target;
    private int cursor;

    private GifCleaner(byte[] source) {
        this.source = source;
        this.target = new ByteArrayOutputStream(source.length);
    }

    /** Temizlenmiş GIF; {@code width}/{@code height} mantıksal ekran boyutu, {@code frames} kare sayısıdır. */
    record Result(byte[] bytes, int width, int height, int frames) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Result that && width == that.width && height == that.height
                    && frames == that.frames && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, frames, Arrays.hashCode(bytes));
        }

        @Override
        public String toString() {
            return "GifCleaner.Result[" + width + "x" + height + ", frames=" + frames + ", size=" + bytes.length + "]";
        }
    }

    /** "GIF87a" ya da "GIF89a" ile başlıyor mu? */
    static boolean hasSignature(byte[] data) {
        return data.length >= HEADER_SIZE
                && (Arrays.equals(data, 0, HEADER_SIZE, GIF89A, 0, HEADER_SIZE)
                || Arrays.equals(data, 0, HEADER_SIZE, GIF87A, 0, HEADER_SIZE));
    }

    static Result clean(byte[] data, int maxFrames) {
        if (!hasSignature(data)) {
            throw new MalformedGifException();
        }
        return new GifCleaner(data).walk(maxFrames);
    }

    private Result walk(int maxFrames) {
        copy(HEADER_SIZE);
        int width = littleEndian16(cursor);
        int height = littleEndian16(cursor + 2);
        int screenFlags = byteAt(cursor + 4);
        copy(SCREEN_SIZE);
        copyColorTable(screenFlags);
        int frames = 0;
        for (int block = byteAt(cursor++); block != TRAILER; block = byteAt(cursor++)) {
            if (block == IMAGE) {
                frames++;
                if (frames > maxFrames) {
                    throw new MalformedGifException();
                }
                copyFrame();
            } else if (block == EXTENSION) {
                copyOrDropExtension();
            } else {
                throw new MalformedGifException();
            }
        }
        target.write(TRAILER);
        if (frames == 0 || width == 0 || height == 0) {
            throw new MalformedGifException();
        }
        return new Result(target.toByteArray(), width, height, frames);
    }

    private void copyFrame() {
        int frameFlags = byteAt(cursor + IMAGE_DESCRIPTOR_SIZE - 1);
        target.write(IMAGE);
        copy(IMAGE_DESCRIPTOR_SIZE);
        copyColorTable(frameFlags);
        copy(1); // LZW en küçük kod uzunluğu
        copySubBlocks();
    }

    private void copyOrDropExtension() {
        int label = byteAt(cursor++);
        if (label == GRAPHIC_CONTROL || (label == APPLICATION && startsWithLoopId())) {
            target.write(EXTENSION);
            target.write(label);
            copySubBlocks();
        } else {
            skipSubBlocks();
        }
    }

    /** Uygulama bloğunun ilk alt bloğu 11 baytlık kimliktir; yalnız döngü kimliği korunur. */
    private boolean startsWithLoopId() {
        int size = byteAt(cursor);
        int start = cursor + 1;
        return size == LOOP_ID.length && start + size <= source.length
                && Arrays.equals(source, start, start + size, LOOP_ID, 0, LOOP_ID.length);
    }

    /** Renk tablosu bayrağı açıksa 3 x 2^(n+1) baytlık tabloyu kopyalar. */
    private void copyColorTable(int flags) {
        if ((flags & 0x80) != 0) {
            copy(3 << ((flags & 0x07) + 1));
        }
    }

    /** Uzunluk baytı + veri dizisi; 0 uzunluklu blok diziyi bitirir. */
    private void copySubBlocks() {
        int size;
        do {
            size = byteAt(cursor);
            copy(size + 1);
        } while (size != 0);
    }

    private void skipSubBlocks() {
        int size;
        do {
            size = byteAt(cursor);
            cursor += size + 1;
            if (cursor > source.length) {
                throw new MalformedGifException();
            }
        } while (size != 0);
    }

    private void copy(int length) {
        if (cursor + length > source.length) {
            throw new MalformedGifException();
        }
        target.write(source, cursor, length);
        cursor += length;
    }

    private int byteAt(int index) {
        if (index < 0 || index >= source.length) {
            throw new MalformedGifException();
        }
        return source[index] & 0xFF;
    }

    private int littleEndian16(int index) {
        return byteAt(index) | byteAt(index + 1) << 8;
    }

    /** GIF yapısı geçersiz; ayrıntı istemciye yansıtılmaz. */
    static final class MalformedGifException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        MalformedGifException() {
            super("Malformed GIF", null, false, false);
        }
    }
}
