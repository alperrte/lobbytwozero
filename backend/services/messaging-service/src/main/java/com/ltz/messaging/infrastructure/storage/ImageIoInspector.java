package com.ltz.messaging.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ltz.messaging.application.port.ImageInspectorPort;
import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.ltz.messaging.infrastructure.config.AttachmentProperties;

/**
 * JDK {@code ImageIO} ile (yeni bağımlılık yok) mesaj görseli doğrulama ve temizleme (MSG-17, SECURITY.md §13):
 * <ul>
 *   <li>Biçim ilk baytlardan belirlenir (PNG, JPEG, GIF) ve bildirilen {@code Content-Type} ile aynı olmalıdır; SVG, HTML,
 *       WebP ve tanınmayan her şey reddedilir.</li>
 *   <li>PNG/JPEG: kenar ve toplam piksel sınırı başlıktan, görsel çözülmeden denetlenir (dekompresyon bombası). Görsel
 *       çözülür, JPEG'in EXIF yönü uygulanır ve aynı biçimde yeniden kodlanır; EXIF/konum ve gömülü veri atılır. Sonuç
 *       1 MB'ı aşarsa birkaç adımda orantılı küçültülür, yine aşarsa {@code IMAGE_TOO_LARGE}.</li>
 *   <li>GIF yeniden kodlanmaz (animasyon korunur); blok yapısı doğrulanıp temizlenir ({@link GifCleaner}). Kenar, kare
 *       sayısı ve kenar x kenar x kare sınırı istemcide çözme bombasını keser.</li>
 *   <li>Aynı anda en çok {@code concurrency} görsel işlenir; sıra beklenenden uzun sürerse {@code MEDIA_BUSY}.</li>
 * </ul>
 */
@Component
public class ImageIoInspector implements ImageInspectorPort {

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";
    static final String GIF = "image/gif";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int SHRINK_ATTEMPTS = 4;
    private static final double SHRINK_FACTOR = 0.75;

    private final AttachmentProperties properties;
    private final Semaphore slots;

    @Autowired
    public ImageIoInspector(AttachmentProperties properties) {
        this(properties, new Semaphore(properties.concurrency(), true));
    }

    /** Testte işleme yuvaları dışarıdan tutulabilsin diye. */
    ImageIoInspector(AttachmentProperties properties, Semaphore slots) {
        this.properties = properties;
        this.slots = slots;
    }

    @Override
    public ProcessedImage process(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new MessagingException(MessagingErrorCode.INVALID_IMAGE);
        }
        if (content.length > MessageAttachment.MAX_SIZE_BYTES) {
            throw new MessagingException(MessagingErrorCode.IMAGE_TOO_LARGE);
        }
        String detected = detect(content);
        if (detected == null || !detected.equals(normalize(declaredContentType))) {
            throw new MessagingException(MessagingErrorCode.INVALID_IMAGE);
        }
        acquireSlot();
        try {
            return GIF.equals(detected) ? gif(content) : raster(content, detected);
        } finally {
            slots.release();
        }
    }

    static String detect(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return PNG;
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        return GifCleaner.hasSignature(content) ? GIF : null;
    }

    private ProcessedImage gif(byte[] content) {
        GifCleaner.Result cleaned;
        try {
            cleaned = GifCleaner.clean(content, properties.maxGifFrames());
        } catch (GifCleaner.MalformedGifException _) {
            throw new MessagingException(MessagingErrorCode.INVALID_IMAGE);
        }
        long totalPixels = (long) cleaned.width() * cleaned.height() * cleaned.frames();
        if (cleaned.width() > properties.maxGifSide() || cleaned.height() > properties.maxGifSide()
                || totalPixels > properties.maxGifPixels()) {
            throw new MessagingException(MessagingErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        }
        return new ProcessedImage(GIF, cleaned.bytes(), cleaned.width(), cleaned.height(), cleaned.frames());
    }

    private ProcessedImage raster(byte[] content, String mimeType) {
        ImageReader reader = readerFor(mimeType);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            requireWithinLimits(reader.getWidth(0), reader.getHeight(0));
            BufferedImage decoded = reader.read(0);
            int orientation = JPEG.equals(mimeType) ? JpegOrientation.of(content) : JpegOrientation.NONE;
            return encodeWithinLimit(RasterImages.orient(decoded, orientation), mimeType);
        } catch (MessagingException exception) {
            throw exception;
        } catch (IOException | RuntimeException _) {
            // Bozuk, kesik ya da desteklenmeyen (ör. CMYK JPEG) görsel; ayrıntı istemciye yansıtılmaz.
            throw new MessagingException(MessagingErrorCode.INVALID_IMAGE);
        } finally {
            reader.dispose();
        }
    }

    private void requireWithinLimits(int width, int height) {
        boolean tooLarge = width > properties.maxSide() || height > properties.maxSide()
                || (long) width * height > properties.maxPixels();
        if (width < 1 || height < 1 || tooLarge) {
            throw new MessagingException(MessagingErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        }
    }

    /** Yeniden kodlanan görsel 1 MB'ı aşarsa (PNG yeniden kodlaması kaynaktan büyük olabilir) orantılı küçültür. */
    private static ProcessedImage encodeWithinLimit(BufferedImage image, String mimeType) throws IOException {
        boolean png = PNG.equals(mimeType);
        BufferedImage current = image;
        for (int attempt = 0; attempt <= SHRINK_ATTEMPTS; attempt++) {
            byte[] encoded = RasterImages.encode(current, png);
            if (encoded.length <= MessageAttachment.MAX_SIZE_BYTES) {
                return new ProcessedImage(mimeType, encoded, current.getWidth(), current.getHeight(), 1);
            }
            current = RasterImages.shrink(current, SHRINK_FACTOR);
        }
        throw new MessagingException(MessagingErrorCode.IMAGE_TOO_LARGE);
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(properties.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new MessagingException(MessagingErrorCode.MEDIA_BUSY);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new MessagingException(MessagingErrorCode.MEDIA_BUSY);
        }
    }

    private static ImageReader readerFor(String mimeType) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(mimeType);
        if (!readers.hasNext()) {
            throw new MessagingException(MessagingErrorCode.INVALID_IMAGE);
        }
        return readers.next();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    /** {@code image/png; charset=x} → {@code image/png}. */
    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
