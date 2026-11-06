package com.ltz.content.api.dto.response;

import com.ltz.content.domain.enums.CategoryKind;

public record CategoryResponse(String code, CategoryKind kind, String nameTr, String nameEn) {
}
