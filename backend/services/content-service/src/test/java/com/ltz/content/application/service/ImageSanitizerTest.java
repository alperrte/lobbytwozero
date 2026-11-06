package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.ltz.content.TestImages;
import com.ltz.content.application.service.ImageSanitizer.SanitizedImage;
import com.ltz.content.domain.entity.ContentImage;
import com.ltz.content.domain.exception.InvalidImageException;
import com.ltz.content.domain.exception.InvalidImageException.Reason;

class ImageSanitizerTest {

	private final ImageSanitizer sanitizer = new ImageSanitizer();

	private static void assertRejected(ImageSanitizer sanitizer, byte[] upload, Reason reason) {
		assertThatThrownBy(() -> sanitizer.sanitize(upload))
				.isInstanceOfSatisfying(InvalidImageException.class, exception -> assertThat(exception.getReason()).isEqualTo(reason));
	}

	@Test
	void pngIsAcceptedAndReencoded() throws IOException {
		byte[] upload = TestImages.png(40, 20);

		SanitizedImage image = sanitizer.sanitize(upload);

		assertThat(image.mimeType()).isEqualTo("image/png");
		assertThat(image.width()).isEqualTo(40);
		assertThat(image.height()).isEqualTo(20);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.content()));
		assertThat(decoded.getWidth()).isEqualTo(40);
	}

	@Test
	void jpegIsAcceptedAndReencoded() throws IOException {
		SanitizedImage image = sanitizer.sanitize(TestImages.jpeg(30, 30));

		assertThat(image.mimeType()).isEqualTo("image/jpeg");
		assertThat(image.width()).isEqualTo(30);
		assertThat(ImageIO.read(new ByteArrayInputStream(image.content()))).isNotNull();
	}

	@Test
	void trailingPayloadAfterTheImageIsDroppedByReencoding() throws IOException {
		byte[] png = TestImages.png(10, 10);
		byte[] polyglot = Arrays.copyOf(png, png.length + 40);
		byte[] payload = "<script>alert(1)</script>".getBytes();
		System.arraycopy(payload, 0, polyglot, png.length, payload.length);

		SanitizedImage image = sanitizer.sanitize(polyglot);

		assertThat(new String(image.content(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
	}

	@Test
	void svgAndHtmlAreRejectedEvenWhenTheyArriveAsImages() {
		assertRejected(sanitizer, TestImages.svgWithScript(), Reason.UNSUPPORTED_TYPE);
		assertRejected(sanitizer, TestImages.html(), Reason.UNSUPPORTED_TYPE);
	}

	@Test
	void otherImageFormatsAreRejected() throws IOException {
		assertRejected(sanitizer, TestImages.gif(10, 10), Reason.UNSUPPORTED_TYPE);
	}

	@Test
	void emptyAndMissingUploadsAreRejected() {
		assertRejected(sanitizer, new byte[0], Reason.EMPTY);
		assertRejected(sanitizer, null, Reason.EMPTY);
	}

	@Test
	void oversizedUploadIsRejectedBeforeDecoding() {
		assertRejected(sanitizer, new byte[ContentImage.MAX_SIZE_BYTES + 1], Reason.TOO_LARGE);
	}

	@Test
	void tooManyPixelsAreRejectedWithoutDecodingTheImage() throws IOException {
		assertRejected(sanitizer, TestImages.png(ContentImage.MAX_SIDE_PIXELS + 1, 1), Reason.DIMENSIONS_EXCEEDED);
		// Kenarlar tek tek sınır içinde ama toplam piksel sınırı aşılıyor (5000 x 5000 = 25 milyon).
		assertRejected(sanitizer, TestImages.image("png", 5000, 5000, BufferedImage.TYPE_BYTE_BINARY), Reason.DIMENSIONS_EXCEEDED);
	}

	@Test
	void truncatedImageIsCorrupt() throws IOException {
		byte[] png = TestImages.png(40, 40);

		assertRejected(sanitizer, Arrays.copyOf(png, png.length / 2), Reason.CORRUPT);
	}

	@Test
	void sanitizedImageHasValueSemanticsAndHidesBytesInToString() {
		SanitizedImage first = new SanitizedImage(new byte[] { 1, 2 }, "image/png", 1, 2);
		SanitizedImage same = new SanitizedImage(new byte[] { 1, 2 }, "image/png", 1, 2);
		SanitizedImage other = new SanitizedImage(new byte[] { 9 }, "image/png", 1, 2);

		assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other).isNotEqualTo("x");
		assertThat(first.toString()).contains("image/png").doesNotContain("[1, 2]");
	}
}
