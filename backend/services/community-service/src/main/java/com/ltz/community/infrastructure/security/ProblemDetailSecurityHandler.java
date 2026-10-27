package com.ltz.community.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Spring Security hatalarını (401/403) sabit içerikli ProblemDetail (RFC 9457) gövdesiyle döndürür. */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		// Kimlik bilgisi göndermeyen yazma isteği CSRF filtresine takılsa da kimliksiz (401) sayılır.
		if (accessDeniedException instanceof CsrfException && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
			write(response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
			return;
		}
		write(response, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
				+ "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
	}
}
