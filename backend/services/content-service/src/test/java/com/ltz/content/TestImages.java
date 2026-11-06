package com.ltz.content;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

/** Testler için bellekte küçük görseller ve görsel gibi görünen zararlı yükler üretir. */
public final class TestImages {

	private TestImages() {
	}

	public static byte[] image(String format, int width, int height, int type) throws IOException {
		BufferedImage image = new BufferedImage(width, height, type);
		for (int x = 0; x < width; x++) {
			image.setRGB(x, 0, 0x336699 + x);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	public static byte[] png(int width, int height) throws IOException {
		return image("png", width, height, BufferedImage.TYPE_INT_RGB);
	}

	public static byte[] jpeg(int width, int height) throws IOException {
		return image("jpeg", width, height, BufferedImage.TYPE_INT_RGB);
	}

	public static byte[] gif(int width, int height) throws IOException {
		return image("gif", width, height, BufferedImage.TYPE_INT_RGB);
	}

	/** Betik içeren SVG: tarayıcıda açılırsa çalışır; sunucu reddetmelidir. */
	public static byte[] svgWithScript() {
		return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><script>alert(1)</script></svg>"
				.getBytes(StandardCharsets.UTF_8);
	}

	public static byte[] html() {
		return "<!doctype html><html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);
	}
}
