package com.ltz.user;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** Testlerde gerçek, geçerli görseller üretir (repoda ikili dosya tutulmaz). */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return write(filled(width, height, BufferedImage.TYPE_INT_RGB, new Color(30, 120, 200)), "png");
    }

    /** Yarısı saydam piksel içeren PNG. */
    public static byte[] transparentPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(200, 40, 40, 128));
        graphics.fillRect(0, 0, width / 2, height);
        graphics.dispose();
        return write(image, "png");
    }

    /** Alfa kanallı ama tamamen opak PNG (JPEG'e çevrilmelidir). */
    public static byte[] opaqueAlphaPng(int width, int height) {
        return write(filled(width, height, BufferedImage.TYPE_INT_ARGB, new Color(10, 200, 90, 255)), "png");
    }

    /** Çok büyük piksel sayılı ama sıkıştığı için küçük dosya (1 bit). */
    public static byte[] monochromePng(int width, int height) {
        return write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return write(filled(width, height, BufferedImage.TYPE_INT_RGB, new Color(220, 160, 20)), "jpg");
    }

    /** Düz renkli JPEG; SOI'den hemen sonra EXIF yönlendirme etiketi (APP1) eklenir. */
    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        byte[] jpeg = jpeg(width, height);
        byte[] exif = exifSegment(orientation);
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Büyük endian TIFF başlıklı tek girişli (0x0112) EXIF APP1 bölümü. */
    private static byte[] exifSegment(int orientation) {
        byte[] tiff = {
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00};
        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.length;
        byte[] segment = new byte[2 + length];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xE1;
        segment[2] = (byte) (length >> 8);
        segment[3] = (byte) length;
        System.arraycopy(header, 0, segment, 4, header.length);
        System.arraycopy(tiff, 0, segment, 4 + header.length, tiff.length);
        return segment;
    }

    /**
     * El yapımı GIF89a: döngü bloğu, isteğe bağlı yorum bloğu ve {@code frames} adet 1x1 kare. Ekran boyutu
     * {@code canvasWidth x canvasHeight} olarak bildirilir.
     */
    public static byte[] gif(int frames, boolean withComment, int canvasWidth, int canvasHeight) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {'G', 'I', 'F', '8', '9', 'a'});
        out.writeBytes(new byte[] {(byte) canvasWidth, (byte) (canvasWidth >> 8), (byte) canvasHeight,
                (byte) (canvasHeight >> 8), (byte) 0x80, 0x00, 0x00});
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00}); // 2 renkli tablo
        out.writeBytes(new byte[] {0x21, (byte) 0xFF, 0x0B, 'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0',
                0x03, 0x01, 0x00, 0x00, 0x00});
        if (withComment) {
            out.writeBytes(new byte[] {0x21, (byte) 0xFE, 0x05, 'h', 'e', 'l', 'l', 'o', 0x00});
        }
        for (int i = 0; i < frames; i++) {
            out.writeBytes(new byte[] {0x21, (byte) 0xF9, 0x04, 0x01, 0x0A, 0x00, 0x00, 0x00}); // kare süresi
            out.writeBytes(new byte[] {0x2C, 0, 0, 0, 0, 0x01, 0x00, 0x01, 0x00, 0x00}); // 1x1 kare
            out.writeBytes(new byte[] {0x02, 0x02, 0x44, 0x01, 0x00}); // LZW verisi
        }
        out.write(0x3B);
        return out.toByteArray();
    }

    private static BufferedImage filled(int width, int height, int type, Color color) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, Math.max(1, width / 4), Math.max(1, height / 4));
        graphics.dispose();
        return image;
    }

    private static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }
}
