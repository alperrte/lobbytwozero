package com.ltz.social.application.service;

import java.util.UUID;

/** {@code userId} ile {@code otherUserId} arasında, herhangi bir yönde engel olup olmadığı. */
public record BlockCheckResult(UUID userId, boolean blocked) {
}
