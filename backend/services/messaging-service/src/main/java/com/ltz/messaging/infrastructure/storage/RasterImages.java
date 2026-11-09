package com.ltz.messaging.infrastructure.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * PNG/JPEG için durumsuz dönüşümler: EXIF yönünü uygulama, orantılı küçültme ve yeniden kodlama. Kodlama yalnız piksel
 * verisini yazar; EXIF, metin parçaları ve diğer metadata çıktıya geçmez. Kaynak görsel değiştirilmez.
 */
final class RasterImages {

    private static final float JPEG_QUALITY = 0.85f;
    /** Ayna gerektiren EXIF yönleri (2, 4, 5, 7). */
    private static final Set<Integer> MIRRORED = Set.of(2, 4, 5, 7);

    private RasterImages() {
    }

    /**
     * EXIF yönünü uygular: gerekiyorsa önce yatay ayna, sonra saat yönünde çeyrek dönüş. Yön 1 ise kaynak döner.
     * Çeyrek dönüş sayıları: 3 ve 4 → 2; 6 ve 7 → 1; 5 ve 8 → 3.
     */
    static BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == JpegOrientation.NONE) {
            return source;
        }
        int quarterTurns = switch (orientation) {
            case 3, 4 -> 2;
            case 6, 7 -> 1;
            case 5, 8 -> 3;
            default -> 0;
        };
        int width = source.getWidth();
        int height = source.getHeight();
        boolean sideways = quarterTurns % 2 == 1;
        // Dönüşten sonra görsel yeniden pozitif koordinatlara taşınır.
        AffineTransform transform = switch (quarterTurns) {
            case 1 -> AffineTransform.getTranslateInstance(height, 0);
            case 2 -> AffineTransform.getTranslateInstance(width, height);
            case 3 -> AffineTransform.getTranslateInstance(0, width);
            default -> new AffineTransform();
        };
        transform.quadrantRotate(quarterTurns);
        if (MIRRORED.contains(orientation)) {
            transform.translate(width, 0);
            transform.scale(-1, 1);
        }
        BufferedImage target = canvas(sideways ? height : width, sideways ? width : height, source);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** Görseli {@code factor} oranında (0-1) küçültür; en küçük kenar 1 pikseldir. */
    static BufferedImage shrink(BufferedImage source, double factor) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage target = canvas(width, height, source);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** PNG (saydamlık korunur) ya da JPEG olarak kodlar. JPEG'te saydam alanlar beyaza düzlenir. */
    static byte[] encode(BufferedImage image, boolean png) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (png) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG writer is not available.");
            }
            return output.toByteArray();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(opaque(image), null, null), parameters);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static BufferedImage opaque(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static BufferedImage canvas(int width, int height, BufferedImage like) {
        return new BufferedImage(width, height,
                like.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }
}
