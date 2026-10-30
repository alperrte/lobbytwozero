package com.ltz.user.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.stereotype.Component;

import com.ltz.user.application.port.ImageInspectorPort;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.config.MediaProperties;

/**
 * Yeni bağımlılık olmadan (JDK {@code ImageIO}) görsel doğrulama ve küçültme. Düşük CPU/bellek için tasarım:
 * <ul>
 *   <li>Biçim ilk baytlardan (magic byte) belirlenir; SVG, HTML ve tanınmayan her şey reddedilir.</li>
 *   <li>Boyut, görsel çözülmeden başlıktan okunur; kenar ve toplam piksel sınırı dekompresyon bombasını keser.</li>
 *   <li>JPEG/PNG hedef boyuta yakın çözünürlükte, <b>alt örnekleyerek</b> açılır (40 MP'lik fotoğraf tam boyutta ~160 MB
 *       yerine birkaç MB'a iner), sonra çok adımlı küçültülür ({@link ImageTransforms}).</li>
 *   <li>Yeniden kodlama EXIF/konum bilgisini ve gizli (polyglot) içeriği temizler; JPEG'in EXIF yönlendirmesi uygulanır.</li>
 *   <li>İki boyutlu çıktı: büyük (profil) ve küçük (liste/arama). Saydamlık yoksa progressive JPEG, varsa PNG.</li>
 *   <li>GIF yeniden kodlanmaz; yalnız yapısı doğrulanıp temizlenir ({@link GifSanitizer}).</li>
 *   <li>Aynı anda en çok {@code concurrency} görsel işlenir; sıra dolarsa kısa beklenir, sonra {@code MEDIA_BUSY}.</li>
 * </ul>
 */
@Component
public class ImageIoInspector implements ImageInspectorPort {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String GIF = "image/gif";
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final MediaProperties properties;
    private final Semaphore slots;

    public ImageIoInspector(MediaProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(properties.concurrency(), true);
    }

    @Override
    public ProcessedImage process(MediaKind kind, byte[] content, String declaredContentType) {
        String detected = detect(content);
        if (detected == null || !detected.equals(normalize(declaredContentType))) {
            throw new UserException(UserErrorCode.INVALID_IMAGE);
        }
        acquire();
        try {
            return GIF.equals(detected) ? gif(kind, content) : raster(kind, content, detected);
        } finally {
            slots.release();
        }
    }

    // ------------------------------------------------------------ GIF

    private ProcessedImage gif(MediaKind kind, byte[] content) {
        GifSanitizer.Result result;
        try {
            result = GifSanitizer.sanitize(content, properties.maxFrames());
        } catch (GifSanitizer.InvalidGifException _) {
            throw new UserException(UserErrorCode.INVALID_IMAGE);
        }
        int limit = kind == MediaKind.AVATAR ? properties.avatarLarge() : properties.coverLarge();
        if (result.width() > limit || result.height() > limit) {
            throw new UserException(UserErrorCode.INVALID_IMAGE_DIMENSIONS);
        }
        return new ProcessedImage("gif", GIF, result.bytes(), null, result.width(), result.height(), result.frames());
    }

    // ------------------------------------------------------------ JPEG / PNG

    private ProcessedImage raster(MediaKind kind, byte[] content, String contentType) {
        ImageReader reader = readerFor(contentType);
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(stream, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            checkDimensions(width, height);
            int orientation = JPEG.equals(contentType) ? ExifOrientation.read(content) : ExifOrientation.NORMAL;
            BufferedImage source = decode(reader, kind, width, height);
            return encode(kind, ImageTransforms.orient(source, orientation));
        } catch (UserException exception) {
            throw exception;
        } catch (IOException | RuntimeException _) {
            // Bozuk, kesik ya da desteklenmeyen (ör. CMYK) görsel; ayrıntı istemciye yansıtılmaz.
            throw new UserException(UserErrorCode.INVALID_IMAGE);
        } finally {
            reader.dispose();
        }
    }

    private void checkDimensions(int width, int height) {
        boolean tooBig = width > properties.maxDimension() || height > properties.maxDimension()
                || (long) width * height > properties.maxPixels();
        if (width < 1 || height < 1 || tooBig) {
            throw new UserException(UserErrorCode.INVALID_IMAGE_DIMENSIONS);
        }
    }

    /** Hedef boyuta yetecek en büyük tam sayı alt örnekleme çarpanıyla okur; tam çözünürlük hiç belleğe alınmaz. */
    private BufferedImage decode(ImageReader reader, MediaKind kind, int width, int height) throws IOException {
        int factor = kind == MediaKind.AVATAR
                ? Math.max(1, Math.min(width, height) / properties.avatarLarge())
                : Math.max(1, Math.max(width, height) / properties.coverLarge());
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(factor, factor, 0, 0);
        return reader.read(0, param);
    }

    private ProcessedImage encode(MediaKind kind, BufferedImage oriented) throws IOException {
        BufferedImage large;
        BufferedImage small;
        if (kind == MediaKind.AVATAR) {
            BufferedImage square = ImageTransforms.centerSquare(oriented);
            large = ImageTransforms.fit(square, properties.avatarLarge(), properties.avatarLarge());
            small = ImageTransforms.fit(large, properties.avatarSmall(), properties.avatarSmall());
        } else {
            large = ImageTransforms.fit(oriented, properties.coverLarge(), properties.coverLarge());
            small = ImageTransforms.fit(large, properties.coverSmall(), properties.coverSmall());
        }
        boolean transparent = ImageTransforms.hasTransparency(large);
        return new ProcessedImage(transparent ? "png" : "jpg", transparent ? PNG : JPEG,
                ImageTransforms.encode(large, transparent), ImageTransforms.encode(small, transparent),
                large.getWidth(), large.getHeight(), 1);
    }

    // ------------------------------------------------------------ yardımcılar

    private static ImageReader readerFor(String contentType) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
        if (!readers.hasNext()) {
            throw new UserException(UserErrorCode.INVALID_IMAGE);
        }
        return readers.next();
    }

    private void acquire() {
        try {
            if (!slots.tryAcquire(properties.busyWait().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new UserException(UserErrorCode.MEDIA_BUSY);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new UserException(UserErrorCode.MEDIA_BUSY);
        }
    }

    /** İlk baytlardan gerçek biçim; tanınmıyorsa {@code null}. */
    static String detect(byte[] data) {
        if (startsWith(data, JPEG_SIGNATURE)) {
            return JPEG;
        }
        if (startsWith(data, PNG_SIGNATURE)) {
            return PNG;
        }
        return GifSanitizer.hasGifSignature(data) ? GIF : null;
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length && Arrays.equals(data, 0, signature.length, signature, 0, signature.length);
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String base = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        return base.strip().toLowerCase(Locale.ROOT);
    }
}
