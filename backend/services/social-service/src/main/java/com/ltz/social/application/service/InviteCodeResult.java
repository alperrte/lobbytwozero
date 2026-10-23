package com.ltz.social.application.service;

/** {@code created}: bu çağrıda yeni kod üretildi mi (HTTP 201) yoksa mevcut etkin kod mu döndü (HTTP 200). */
public record InviteCodeResult(InviteCodeView code, boolean created) {
}
