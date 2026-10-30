package com.ltz.user.infrastructure.storage;

/**
 * JPEG dosyasının EXIF yönlendirme etiketini (0x0112) okur. {@code ImageIO} bu etiketi uygulamaz; telefon fotoğrafları
 * yan dönük görünmesin diye değer ayrıca okunup görsele uygulanır. Bozuk ya da eksik veri {@code 1} (dönüşüm yok) döner;
 * yalnız ilk APP1 bölümüne bakılır ve hiçbir okuma sınır dışına taşmaz.
 */
final class ExifOrientation {

    static final int NORMAL = 1;
    private static final int ORIENTATION_TAG = 0x0112;
    private static final int TYPE_SHORT = 3;
    private static final int MAX_ORIENTATION = 8;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    private ExifOrientation() {
    }

    static int read(byte[] jpeg) {
        int position = 2; // SOI (FFD8) sonrası
        while (position + 4 <= jpeg.length && (jpeg[position] & 0xFF) == 0xFF) {
            int marker = jpeg[position + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                return NORMAL; // görüntü verisi başladı: EXIF yok
            }
            int length = ((jpeg[position + 2] & 0xFF) << 8) | (jpeg[position + 3] & 0xFF);
            if (length < 2 || position + 2 + length > jpeg.length) {
                return NORMAL;
            }
            if (marker == 0xE1 && startsWithExif(jpeg, position + 4, length - 2)) {
                return fromTiff(jpeg, position + 4 + EXIF_HEADER.length, length - 2 - EXIF_HEADER.length);
            }
            position += 2 + length;
        }
        return NORMAL;
    }

    private static boolean startsWithExif(byte[] data, int offset, int available) {
        if (available < EXIF_HEADER.length) {
            return false;
        }
        for (int i = 0; i < EXIF_HEADER.length; i++) {
            if (data[offset + i] != EXIF_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    private static int fromTiff(byte[] data, int start, int available) {
        if (available < 8) {
            return NORMAL;
        }
        boolean littleEndian;
        if (hasMarker(data, start, 'I')) {
            littleEndian = true;
        } else if (hasMarker(data, start, 'M')) {
            littleEndian = false;
        } else {
            return NORMAL;
        }
        long ifdOffset = unsigned32(data, start + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset + 2 > available) {
            return NORMAL;
        }
        return readEntries(data, start, available, (int) ifdOffset, littleEndian);
    }

    private static boolean hasMarker(byte[] data, int start, char marker) {
        return data[start] == marker && data[start + 1] == marker;
    }

    private static int readEntries(byte[] data, int start, int available, int ifdOffset, boolean littleEndian) {
        int entries = unsigned16(data, start + ifdOffset, littleEndian);
        int entryStart = start + ifdOffset + 2;
        for (int i = 0; i < entries; i++) {
            int entry = entryStart + i * 12;
            if (entry + 12 > start + available) {
                return NORMAL;
            }
            if (unsigned16(data, entry, littleEndian) == ORIENTATION_TAG
                    && unsigned16(data, entry + 2, littleEndian) == TYPE_SHORT) {
                int value = unsigned16(data, entry + 8, littleEndian);
                return value >= 1 && value <= MAX_ORIENTATION ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int unsigned16(byte[] data, int offset, boolean littleEndian) {
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return littleEndian ? (second << 8) | first : (first << 8) | second;
    }

    private static long unsigned32(byte[] data, int offset, boolean littleEndian) {
        long low = unsigned16(data, littleEndian ? offset : offset + 2, littleEndian);
        long high = unsigned16(data, littleEndian ? offset + 2 : offset, littleEndian);
        return (high << 16) | low;
    }
}
