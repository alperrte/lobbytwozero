package com.ltz.messaging.application.service;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/** Liste uçlarında sayfa boyutu: verilmezse varsayılan, aralık dışıysa {@code 400 INVALID_PAGE_SIZE}. */
final class PageSizes {

    private PageSizes() {
    }

    static int resolve(Integer requested, int defaultSize, int maxSize) {
        if (requested == null) {
            return defaultSize;
        }
        if (requested < 1 || requested > maxSize) {
            throw new MessagingException(MessagingErrorCode.INVALID_PAGE_SIZE);
        }
        return requested;
    }
}
