package com.ltz.user.infrastructure.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * GIF'i çözmeden (CPU yükü yok) yapısını doğrular ve temizler: yalnız kare verisi, grafik denetim blokları (süre/saydamlık)
 * ve döngü bloğu ({@code NETSCAPE2.0}) korunur; yorum, düz metin ve diğer uygulama blokları atılır (gizli veri taşımasın).
 * Kare sayısı ve ekran boyutu sınırlıdır. Bozuk, kesik ya da tanınmayan yapı {@link InvalidGifException} ile reddedilir.
 */
final class GifSanitizer {

    private static final int EXTENSION = 0x21;
    private static final int IMAGE_DESCRIPTOR = 0x2C;
    private static final int TRAILER = 0x3B;
    private static final int GRAPHIC_CONTROL = 0xF9;
    private static final int APPLICATION = 0xFF;
    private static final int HEADER_LENGTH = 6;
    private static final int SCREEN_DESCRIPTOR_LENGTH = 7;
    private static final int IMAGE_DESCRIPTOR_LENGTH = 9;
    private static final String LOOP_APPLICATION = "NETSCAPE2.0";

    private final byte[] input;
    private final ByteArrayOutputStream output;
    private int position;

    private GifSanitizer(byte[] input) {
        this.input = input;
        this.output = new ByteArrayOutputStream(input.length);
    }

    /** Temizlenmiş GIF ve ölçüleri. */
    record Result(byte[] bytes, int width, int height, int frames) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Result that && width == that.width && height == that.height
                    && frames == that.frames && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(width, height, frames) + Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Result[bytes=" + bytes.length + ", width=" + width + ", height=" + height + ", frames=" + frames
                    + "]";
        }
    }

    static boolean hasGifSignature(byte[] data) {
        return data.length >= HEADER_LENGTH && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    static Result sanitize(byte[] data, int maxFrames) {
        return new GifSanitizer(data).run(maxFrames);
    }

    private Result run(int maxFrames) {
        if (!hasGifSignature(input) || input.length < HEADER_LENGTH + SCREEN_DESCRIPTOR_LENGTH) {
            throw new InvalidGifException();
        }
        copy(HEADER_LENGTH);
        int width = u16(position);
        int height = u16(position + 2);
        screenDescriptor();
        int frames = blocks(maxFrames);
        if (frames == 0 || width == 0 || height == 0) {
            throw new InvalidGifException();
        }
        return new Result(output.toByteArray(), width, height, frames);
    }

    /** Mantıksal ekran tanımlayıcısını ve varsa genel renk tablosunu olduğu gibi kopyalar. */
    private void screenDescriptor() {
        int packed = input[position + 4] & 0xFF;
        copy(SCREEN_DESCRIPTOR_LENGTH);
        if ((packed & 0x80) != 0) {
            copy(colorTableLength(packed));
        }
    }

    /** Kapanış işaretine kadar blokları işler ve kare sayısını döner. */
    private int blocks(int maxFrames) {
        int frames = 0;
        while (true) {
            int block = require(position++);
            switch (block) {
                case TRAILER -> {
                    output.write(TRAILER);
                    return frames;
                }
                case EXTENSION -> extension();
                case IMAGE_DESCRIPTOR -> {
                    frames++;
                    if (frames > maxFrames) {
                        throw new InvalidGifException();
                    }
                    frame();
                }
                default -> throw new InvalidGifException();
            }
        }
    }

    private void extension() {
        int label = require(position++);
        if (label == GRAPHIC_CONTROL || (label == APPLICATION && isLoopApplication())) {
            output.write(EXTENSION);
            output.write(label);
            copySubBlocks();
        } else {
            skipSubBlocks();
        }
    }

    /** İlk alt blok uygulama kimliğidir (11 bayt: 8 ad + 3 doğrulama); yalnız {@code NETSCAPE2.0} korunur. */
    private boolean isLoopApplication() {
        int size = require(position);
        if (size != LOOP_APPLICATION.length() || position + 1 + size > input.length) {
            return false;
        }
        String id = new String(input, position + 1, size, StandardCharsets.US_ASCII);
        return LOOP_APPLICATION.equals(id);
    }

    private void frame() {
        output.write(IMAGE_DESCRIPTOR);
        require(position + IMAGE_DESCRIPTOR_LENGTH - 1);
        int packed = input[position + IMAGE_DESCRIPTOR_LENGTH - 1] & 0xFF;
        copy(IMAGE_DESCRIPTOR_LENGTH);
        if ((packed & 0x80) != 0) {
            copy(colorTableLength(packed));
        }
        copy(1); // LZW en küçük kod boyutu
        copySubBlocks();
    }

    private static int colorTableLength(int packed) {
        return 3 * (1 << ((packed & 0x07) + 1));
    }

    private void copySubBlocks() {
        while (true) {
            int size = require(position);
            copy(1);
            if (size == 0) {
                return;
            }
            copy(size);
        }
    }

    private void skipSubBlocks() {
        while (true) {
            int size = require(position++);
            if (size == 0) {
                return;
            }
            position += size;
            if (position > input.length) {
                throw new InvalidGifException();
            }
        }
    }

    private void copy(int count) {
        if (count < 0 || position + count > input.length) {
            throw new InvalidGifException();
        }
        output.write(input, position, count);
        position += count;
    }

    private int require(int index) {
        if (index < 0 || index >= input.length) {
            throw new InvalidGifException();
        }
        return input[index] & 0xFF;
    }

    private int u16(int index) {
        return require(index) | (require(index + 1) << 8);
    }

    /** Yapı bozuk. */
    static final class InvalidGifException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InvalidGifException() {
            super("Invalid GIF structure", null, false, false);
        }
    }
}
