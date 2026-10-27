package com.ltz.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.ltz.community.TestImages.image;
import static com.ltz.community.TestImages.jpeg;
import static com.ltz.community.TestImages.png;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

import com.ltz.community.application.service.ImageSanitizer.SanitizedImage;
import com.ltz.community.domain.entity.PostImage;
import com.ltz.community.domain.exception.InvalidImageException;
import com.ltz.community.domain.exception.InvalidImageException.Reason;

class ImageSanitizerTest {

	private static final String SECRET = "secret-gps-marker";

	private final ImageSanitizer sanitizer = new ImageSanitizer();

	private static byte[] pngWithTextChunk(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
		IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
		entry.setAttribute("keyword", "Comment");
		entry.setAttribute("value", SECRET);
		IIOMetadataNode text = new IIOMetadataNode("tEXt");
		text.appendChild(entry);
		IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
		root.appendChild(text);
		metadata.mergeTree("javax_imageio_png_1.0", root);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, metadata), param);
		}
		writer.dispose();
		return out.toByteArray();
	}

	private static byte[] withJpegComment(byte[] jpeg, String comment) {
		byte[] text = comment.getBytes(StandardCharsets.US_ASCII);
		int length = text.length + 2;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(0xFF);
		out.write(0xFE);
		out.write(length >> 8);
		out.write(length & 0xFF);
		out.write(text, 0, text.length);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	private static boolean contains(byte[] haystack, String needle) {
		return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
	}

	private static BufferedImage decode(byte[] content) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(content));
	}

	private void assertRejected(byte[] upload, Reason reason) {
		assertThatThrownBy(() -> sanitizer.sanitize(upload))
				.isInstanceOfSatisfying(InvalidImageException.class,
						exception -> assertThat(exception.getReason()).isEqualTo(reason));
	}

	@Test
	void jpegIsReEncodedAndKeepsDimensions() throws IOException {
		SanitizedImage result = sanitizer.sanitize(jpeg(64, 32));

		assertThat(result.mimeType()).isEqualTo("image/jpeg");
		assertThat(result.width()).isEqualTo(64);
		assertThat(result.height()).isEqualTo(32);
		BufferedImage decoded = decode(result.content());
		assertThat(decoded.getWidth()).isEqualTo(64);
		assertThat(decoded.getHeight()).isEqualTo(32);
	}

	@Test
	void pngIsReEncodedAndKeepsDimensions() throws IOException {
		SanitizedImage result = sanitizer.sanitize(png(40, 20));

		assertThat(result.mimeType()).isEqualTo("image/png");
		assertThat(result.width()).isEqualTo(40);
		assertThat(result.height()).isEqualTo(20);
		BufferedImage decoded = decode(result.content());
		assertThat(decoded.getWidth()).isEqualTo(40);
		assertThat(decoded.getHeight()).isEqualTo(20);
	}

	@Test
	void pngTextMetadataIsStripped() throws IOException {
		byte[] upload = pngWithTextChunk(16, 16);
		assertThat(contains(upload, SECRET)).isTrue();

		SanitizedImage result = sanitizer.sanitize(upload);

		assertThat(contains(result.content(), SECRET)).isFalse();
	}

	@Test
	void jpegCommentMarkerIsStripped() throws IOException {
		byte[] upload = withJpegComment(jpeg(16, 16), SECRET);
		assertThat(contains(upload, SECRET)).isTrue();

		SanitizedImage result = sanitizer.sanitize(upload);

		assertThat(contains(result.content(), SECRET)).isFalse();
	}

	@Test
	void trailingPayloadAfterImageDataIsDiscarded() throws IOException {
		byte[] jpeg = jpeg(16, 16);
		byte[] polyglot = Arrays.copyOf(jpeg, jpeg.length + 14);
		System.arraycopy("<?php evil ?>".getBytes(StandardCharsets.US_ASCII), 0, polyglot, jpeg.length, 13);

		SanitizedImage result = sanitizer.sanitize(polyglot);

		assertThat(contains(result.content(), "<?php")).isFalse();
	}

	@Test
	void gifIsRejectedAsUnsupportedType() throws IOException {
		assertRejected(image("gif", 8, 8, BufferedImage.TYPE_INT_RGB), Reason.UNSUPPORTED_TYPE);
	}

	@Test
	void textFileIsRejectedAsUnsupportedTypeRegardlessOfName() {
		assertRejected("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8),
				Reason.UNSUPPORTED_TYPE);
	}

	@Test
	void truncatedPngIsRejectedAsCorrupt() throws IOException {
		byte[] png = png(64, 64);

		assertRejected(Arrays.copyOf(png, png.length / 2), Reason.CORRUPT);
	}

	@Test
	void emptyUploadIsRejected() {
		assertRejected(new byte[0], Reason.EMPTY);
		assertRejected(null, Reason.EMPTY);
	}

	@Test
	void uploadLargerThanLimitIsRejectedBeforeDecoding() {
		byte[] upload = new byte[PostImage.MAX_SIZE_BYTES + 1];
		new Random(1).nextBytes(upload);

		assertRejected(upload, Reason.TOO_LARGE);
	}

	@Test
	void imageWiderThanSideLimitIsRejected() throws IOException {
		assertRejected(png(PostImage.MAX_SIDE_PIXELS + 1, 1), Reason.DIMENSIONS_EXCEEDED);
	}

	@Test
	void imageWithTooManyPixelsIsRejectedBeforeDecoding() throws IOException {
		byte[] bomb = image("png", 4000, 4000, BufferedImage.TYPE_BYTE_GRAY);
		assertThat(bomb).hasSizeLessThan(PostImage.MAX_SIZE_BYTES);

		assertRejected(bomb, Reason.DIMENSIONS_EXCEEDED);
	}

	@Test
	void sanitizedImageEqualityHashCodeAndToStringUseContent() {
		SanitizedImage first = new SanitizedImage(new byte[] { 1, 2 }, "image/png", 3, 4);
		SanitizedImage same = new SanitizedImage(new byte[] { 1, 2 }, "image/png", 3, 4);

		assertThat(first)
				.isEqualTo(same)
				.hasSameHashCodeAs(same)
				.isNotEqualTo(new SanitizedImage(new byte[] { 1, 3 }, "image/png", 3, 4))
				.isNotEqualTo(new SanitizedImage(new byte[] { 1, 2 }, "image/jpeg", 3, 4))
				.isNotEqualTo(new SanitizedImage(new byte[] { 1, 2 }, "image/png", 5, 4))
				.isNotEqualTo(new SanitizedImage(new byte[] { 1, 2 }, "image/png", 3, 6))
				.isNotEqualTo("not an image")
				.hasToString("SanitizedImage[mimeType=image/png, 3x4, size=2]");
	}
}
