package com.ltz.community.application.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.stereotype.Component;

import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.exception.InvalidImageException;
import com.ltz.community.domain.exception.InvalidImageException.Reason;

/**
 * Yüklenen görseli sunucuda doğrular: dosya adı/Content-Type yerine içeriğin gerçek biçimine bakar
 * (yalnız JPEG ve PNG), boyut/piksel sınırını çözmeden önce uygular ve görseli yeniden kodlar.
 * Yeniden kodlama EXIF/konum ve diğer gömülü metadata'yı ile polyglot yükleri atar.
 */
@Component
public class ImageSanitizer {

	public record SanitizedImage(byte[] content, String mimeType, int width, int height) {

		@Override
		public boolean equals(Object other) {
			return other instanceof SanitizedImage(byte[] otherContent, String otherMimeType, int otherWidth, int otherHeight)
					&& Arrays.equals(content, otherContent)
					&& Objects.equals(mimeType, otherMimeType)
					&& width == otherWidth && height == otherHeight;
		}

		@Override
		public int hashCode() {
			return Objects.hash(Arrays.hashCode(content), mimeType, width, height);
		}

		@Override
		public String toString() {
			return "SanitizedImage[mimeType=" + mimeType + ", " + width + "x" + height + ", size=" + content.length + "]";
		}
	}

	public SanitizedImage sanitize(byte[] upload) {
		if (upload == null || upload.length == 0) {
			throw new InvalidImageException(Reason.EMPTY);
		}
		if (upload.length > PostImage.MAX_SIZE_BYTES) {
			throw new InvalidImageException(Reason.TOO_LARGE);
		}
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(upload))) {
			ImageReader reader = pickReader(input);
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || width > PostImage.MAX_SIDE_PIXELS || height > PostImage.MAX_SIDE_PIXELS
						|| (long) width * height > PostImage.MAX_PIXELS) {
					throw new InvalidImageException(Reason.DIMENSIONS_EXCEEDED);
				}
				String format = reader.getFormatName().toLowerCase();
				BufferedImage decoded = reader.read(0);
				byte[] encoded = "png".equals(format) ? encodePng(decoded) : encodeJpeg(decoded);
				if (encoded.length > PostImage.MAX_SIZE_BYTES) {
					throw new InvalidImageException(Reason.TOO_LARGE);
				}
				return new SanitizedImage(encoded, "png".equals(format) ? "image/png" : "image/jpeg", width, height);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof InvalidImageException invalid) {
				throw invalid;
			}
			throw new InvalidImageException(Reason.CORRUPT);
		}
	}

	private ImageReader pickReader(ImageInputStream input) throws IOException {
		Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
		while (readers.hasNext()) {
			ImageReader reader = readers.next();
			String format = reader.getFormatName().toLowerCase();
			if ("jpeg".equals(format) || "png".equals(format)) {
				return reader;
			}
			reader.dispose();
		}
		throw new InvalidImageException(Reason.UNSUPPORTED_TYPE);
	}

	private byte[] encodePng(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out)) {
			throw new InvalidImageException(Reason.CORRUPT);
		}
		return out.toByteArray();
	}

	private byte[] encodeJpeg(BufferedImage image) throws IOException {
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rgb.createGraphics();
		try {
			graphics.drawImage(image, 0, 0, Color.WHITE, null);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(rgb, "jpeg", out)) {
			throw new InvalidImageException(Reason.CORRUPT);
		}
		return out.toByteArray();
	}
}
