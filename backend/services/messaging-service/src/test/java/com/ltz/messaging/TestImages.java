package com.ltz.messaging;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Testlerde kullanılan görseller bellekte üretilir; repoda ikili test dosyası yoktur. */
public final class TestImages {

    /** Gömülü metadata'da aranan, temizlikten sonra kalmaması gereken işaret. */
    public static final String SECRET_MARKER = "LTZ-GIZLI-KONUM";

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(solid(width, height, BufferedImage.TYPE_INT_RGB, Color.BLUE), "png");
    }

    /** Sol yarısı saydam PNG. */
    public static byte[] transparentPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, x < width / 2 ? 0x00000000 : 0xFFFF0000);
            }
        }
        return encode(image, "png");
    }

    public static byte[] jpeg(int width, int height) {
        return jpeg(solid(width, height, BufferedImage.TYPE_INT_RGB, Color.GREEN), 0.9f);
    }

    /** Sıkışmayan gürültü; düşük kaliteyle küçük kaydedilir, yüksek kaliteyle yeniden kodlanınca büyür. */
    public static byte[] noisyJpeg(int width, int height, float quality) {
        Random random = new Random(42);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return jpeg(image, quality);
    }

    /** Sol üst köşesi kırmızı, geri kalanı beyaz görsel (yön testleri için). */
    public static BufferedImage markedCorner(int width, int height) {
        BufferedImage image = solid(width, height, BufferedImage.TYPE_INT_RGB, Color.WHITE);
        image.setRGB(0, 0, Color.RED.getRGB());
        return image;
    }

    /**
     * JPEG'in başına (SOI'dan hemen sonra) yön etiketli bir EXIF bölümü ve gizli metin taşıyan bir yorum bölümü ekler.
     */
    public static byte[] withExif(byte[] jpeg, int orientation, boolean littleEndian) {
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4).order(littleEndian ? ByteOrder.LITTLE_ENDIAN
                : ByteOrder.BIG_ENDIAN);
        tiff.put(littleEndian ? (byte) 'I' : (byte) 'M').put(littleEndian ? (byte) 'I' : (byte) 'M');
        tiff.putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        byte[] exif = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff.array());
        byte[] comment = SECRET_MARKER.getBytes(StandardCharsets.US_ASCII);
        return concat(new byte[] {(byte) 0xFF, (byte) 0xD8}, segment(0xE1, exif), segment(0xFE, comment),
                Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    /**
     * {@code frames} kareli GIF; ekran tanımından sonra gizli işaret taşıyan bir yorum bloğu, kapanıştan sonra da
     * {@code trailing} baytları eklenir.
     */
    public static byte[] animatedGif(int frames, int width, int height, String trailing) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < frames; frame++) {
                Color color = frame % 2 == 0 ? Color.RED : Color.BLUE;
                writer.writeToSequence(new IIOImage(solid(width, height, BufferedImage.TYPE_BYTE_INDEXED, color), null,
                        null), null);
            }
            writer.endWriteSequence();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            writer.dispose();
        }
        byte[] gif = output.toByteArray();
        int flags = gif[10] & 0xFF;
        int afterScreen = 13 + ((flags & 0x80) != 0 ? 3 << ((flags & 0x07) + 1) : 0);
        byte[] marker = SECRET_MARKER.getBytes(StandardCharsets.US_ASCII);
        byte[] comment = concat(new byte[] {0x21, (byte) 0xFE, (byte) marker.length}, marker, new byte[] {0});
        return concat(Arrays.copyOfRange(gif, 0, afterScreen), comment,
                Arrays.copyOfRange(gif, afterScreen, gif.length), trailing.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean contains(byte[] data, String text) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(text);
    }

    private static BufferedImage solid(int width, int height, int type, Color color) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        return concat(new byte[] {(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length}, payload);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        return output.toByteArray();
    }
}
