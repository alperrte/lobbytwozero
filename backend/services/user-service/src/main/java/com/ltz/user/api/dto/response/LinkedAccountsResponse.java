package com.ltz.user.api.dto.response;

import java.util.List;

import com.ltz.user.application.service.LinkedAccountView;

/** Sahibin bağlı hesapları; her desteklenen sağlayıcı için bir kayıt. */
public record LinkedAccountsResponse(List<LinkedAccountView> accounts) {
}
