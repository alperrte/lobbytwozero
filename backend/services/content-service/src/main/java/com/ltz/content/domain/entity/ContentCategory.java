package com.ltz.content.domain.entity;

import java.util.UUID;

import com.ltz.content.domain.enums.CategoryKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sabit kategori (V103 ile tohumlanır); uygulama bu tabloya yazmaz. */
@Entity
@Table(name = "content_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentCategory {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false, length = 32)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private CategoryKind kind;

	@Column(name = "name_tr", nullable = false, updatable = false, length = 64)
	private String nameTr;

	@Column(name = "name_en", nullable = false, updatable = false, length = 64)
	private String nameEn;

	@Column(name = "sort_order", nullable = false, updatable = false)
	private int sortOrder;
}
