package com.ltz.lfp.domain.exception;

public class LfpException extends RuntimeException {

    private final LfpErrorCode errorCode;

    public LfpException(LfpErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    /** Dış bağımlılık hatasını istemci sözleşmesine çevirir; neden zincirde (loglar için) kalır, yanıta yazılmaz. */
    public LfpException(LfpErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public LfpErrorCode errorCode() {
        return errorCode;
    }
}
