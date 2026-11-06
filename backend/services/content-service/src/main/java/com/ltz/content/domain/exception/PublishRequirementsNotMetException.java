package com.ltz.content.domain.exception;

import java.util.List;

/**
 * İçerik yayın kurallarını sağlamıyor (CONT-18). {@code issues} her eksiği {@code <dil>:<kod>} biçiminde taşır
 * (ör. {@code tr:BODY_TOO_SHORT}); mesajlar sabittir ve kullanıcı girdisi içermez.
 */
public class PublishRequirementsNotMetException extends RuntimeException {

	private final List<String> issues;

	public PublishRequirementsNotMetException(List<String> issues) {
		super("The content does not meet the publication requirements.");
		this.issues = List.copyOf(issues);
	}

	public List<String> getIssues() {
		return issues;
	}
}
