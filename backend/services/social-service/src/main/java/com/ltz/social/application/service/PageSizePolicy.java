package com.ltz.social.application.service;

import org.springframework.stereotype.Component;

import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.config.FriendRequestProperties;

import lombok.RequiredArgsConstructor;

/** Liste uçlarında sayfa boyutu kuralı: verilmezse varsayılan, aralık dışıysa 400 {@code INVALID_PAGE_SIZE}. */
@Component
@RequiredArgsConstructor
class PageSizePolicy {

    private final FriendRequestProperties properties;

    int resolve(Integer requested) {
        if (requested == null) {
            return properties.defaultPageSize();
        }
        if (requested < 1 || requested > properties.maxPageSize()) {
            throw new SocialException(SocialErrorCode.INVALID_PAGE_SIZE);
        }
        return requested;
    }
}
