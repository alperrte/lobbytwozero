package com.ltz.community.domain.exception;

/** Yüklenen görsel sunucu doğrulamasından geçemedi; mesajlar sabittir, kullanıcı girdisi içermez. */
public class InvalidImageException extends RuntimeException {

	public enum Reason {
		EMPTY("No image file was provided."),
		UNSUPPORTED_TYPE("Only JPEG and PNG images are supported."),
		TOO_LARGE("The image exceeds the maximum allowed size."),
		CORRUPT("The uploaded file is not a valid image."),
		DIMENSIONS_EXCEEDED("The image dimensions exceed the allowed limit.");

		private final String message;

		Reason(String message) {
			this.message = message;
		}
	}

	private final Reason reason;

	public InvalidImageException(Reason reason) {
		super(reason.message);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}
}
