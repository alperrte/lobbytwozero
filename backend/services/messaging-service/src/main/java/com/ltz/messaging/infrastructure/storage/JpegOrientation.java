package com.ltz.messaging.infrastructure.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * JPEG'in EXIF yönlendirme etiketini (0x0112) okur. Yeniden kodlama EXIF'i attığından telefon fotoğrafları yan dönük
 * görünmesin diye değer kodlamadan önce görsele uygulanır ({@link RasterImages#orient}). Yalnız ilk EXIF (APP1)
 * bölümünün ilk IFD'sine bakılır; bozuk ya da eksik veri {@link #NONE} döner, hiçbir okuma bölümün dışına çıkmaz.
 */
final class JpegOrientation {

    /** Dönüşüm yok. */
    static final int NONE = 1;

    private static final int START_OF_SCAN = 0xDA;
    private static final int END_OF_IMAGE = 0xD9;
    private static final int APP1 = 0xE1;
    private static final byte[] EXIF_ID = {'E', 'x', 'i', 'f', 0, 0};
    private static final int ORIENTATION_TAG = 0x0112;
    private static final int SHORT_TYPE = 3;
    private static final int IFD_ENTRY_SIZE = 12;
    private static final int TIFF_HEADER_SIZE = 8;
    private static final short INTEL_ORDER = 0x4949;
    private static final short MOTOROLA_ORDER = 0x4D4D;

    private JpegOrientation() {
    }

    static int of(byte[] jpeg) {
        int position = 2; // SOI
        while (position + 4 <= jpeg.length && (jpeg[position] & 0xFF) == 0xFF) {
            int marker = jpeg[position + 1] & 0xFF;
            if (marker == START_OF_SCAN || marker == END_OF_IMAGE) {
                return NONE;
            }
            int segmentLength = (jpeg[position + 2] & 0xFF) << 8 | jpeg[position + 3] & 0xFF;
            int payload = position + 4;
            int payloadLength = segmentLength - 2;
            if (segmentLength < 2 || payload + payloadLength > jpeg.length) {
                return NONE;
            }
            if (marker == APP1 && isExif(jpeg, payload, payloadLength)) {
                return fromTiff(ByteBuffer.wrap(jpeg, payload + EXIF_ID.length, payloadLength - EXIF_ID.length)
                        .slice());
            }
            position = payload + payloadLength;
        }
        return NONE;
    }

    private static boolean isExif(byte[] jpeg, int payload, int payloadLength) {
        return payloadLength >= EXIF_ID.length + TIFF_HEADER_SIZE
                && Arrays.equals(jpeg, payload, payload + EXIF_ID.length, EXIF_ID, 0, EXIF_ID.length);
    }

    private static int fromTiff(ByteBuffer tiff) {
        short byteOrder = tiff.getShort(0);
        if (byteOrder == INTEL_ORDER) {
            tiff.order(ByteOrder.LITTLE_ENDIAN);
        } else if (byteOrder != MOTOROLA_ORDER) {
            return NONE;
        }
        long firstIfd = Integer.toUnsignedLong(tiff.getInt(4));
        if (firstIfd < TIFF_HEADER_SIZE || firstIfd + 2 > tiff.limit()) {
            return NONE;
        }
        int ifd = (int) firstIfd;
        int entries = Short.toUnsignedInt(tiff.getShort(ifd));
        for (int index = 0; index < entries; index++) {
            int entry = ifd + 2 + index * IFD_ENTRY_SIZE;
            if (entry + IFD_ENTRY_SIZE > tiff.limit()) {
                return NONE;
            }
            if (Short.toUnsignedInt(tiff.getShort(entry)) == ORIENTATION_TAG
                    && Short.toUnsignedInt(tiff.getShort(entry + 2)) == SHORT_TYPE) {
                int value = Short.toUnsignedInt(tiff.getShort(entry + 8));
                return value >= 1 && value <= 8 ? value : NONE;
            }
        }
        return NONE;
    }
}
