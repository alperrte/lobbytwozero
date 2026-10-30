package com.ltz.user.infrastructure.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Saf görsel dönüşümleri: EXIF yönü uygulama, kare kırpma, kaliteli küçültme, saydamlık denetimi ve kodlama. Durum
 * tutmaz; kaynağı değiştirmez, her zaman yeni görsel döner.
 */
final class ImageTransforms {

    private static final float JPEG_QUALITY = 0.85f;
    private static final int OPAQUE = 0xFF;
    private static final int ALPHA_SHIFT = 24;

    private ImageTransforms() {
    }

    /** EXIF yönlendirme değerini (1-8) görsele uygular; 1 ise kaynak aynen döner. */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == ExifOrientation.NORMAL) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage result = blank(swap ? h : w, swap ? w : h, source.getColorModel().hasAlpha());
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    static BufferedImage centerSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        return source.getSubimage(x, y, side, side);
    }

    /** Büyütmez; sığdırmak için gerekiyorsa en-boy oranını koruyarak, kalite için yarıya bölen adımlarla küçültür. */
    static BufferedImage fit(BufferedImage source, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
            width /= 2;
            height /= 2;
            current = resize(current, width, height);
        }
        return resize(current, targetWidth, targetHeight);
    }

    /** En az bir pikseli tam opak olmayan alfa kanallı görsel; alfa kanalı yoksa ya da hepsi opaksa {@code false}. */
    static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> ALPHA_SHIFT) != OPAQUE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** PNG (saydamlık korunur) ya da progressive JPEG olarak kodlar; kodlama EXIF/meta veriyi taşımaz. */
    static byte[] encode(BufferedImage image, boolean png) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (png) {
            if (!ImageIO.write(image, "png", bytes)) {
                throw new IOException("No PNG writer");
            }
            return bytes.toByteArray();
        }
        BufferedImage rgb = image.getColorModel().hasAlpha() ? flatten(image) : image;
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = blank(width, height, source.getColorModel().hasAlpha());
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static BufferedImage blank(int width, int height, boolean alpha) {
        return new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    /** Tamamen opak ama alfa kanallı görseli JPEG'in desteklediği RGB'ye çevirir. */
    private static BufferedImage flatten(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }
}
