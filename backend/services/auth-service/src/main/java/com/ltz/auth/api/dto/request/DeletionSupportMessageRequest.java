package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.Size;

/** Kullanıcının yöneticiye yazdığı isteğe bağlı kısa not (silme talebi) ya da yöneticinin karar notu. */
public record DeletionSupportMessageRequest(@Size(max = AdminReason.MAX_LENGTH) String message) {
}
