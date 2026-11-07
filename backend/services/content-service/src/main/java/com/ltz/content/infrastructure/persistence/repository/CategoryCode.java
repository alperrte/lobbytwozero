package com.ltz.content.infrastructure.persistence.repository;

import java.util.UUID;

/** İçerik-kategori kodu çifti (liste ve detay yanıtları için kapalı projeksiyon). */
public interface CategoryCode {

	UUID getContentId();

	String getCode();
}
