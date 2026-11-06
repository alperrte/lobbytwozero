package com.ltz.content.api.dto.response;

import java.time.LocalDate;

/** İçeriğin kaynağı (CONT-16): kaynak adı, https bağlantısı ve (varsa) kaynak tarihi. */
public record ContentSourceResponse(String title, String url, LocalDate date) {
}
