package com.ltz.auth.api.dto.response;

/** Silme akışının sonraki adımı; e-postanın gönderildiğini veya silmenin tamamlandığını iddia etmez. */
public record AccountDeletionResponse(String nextStep) {

    public static final AccountDeletionResponse CONFIRM_BY_EMAIL = new AccountDeletionResponse("CONFIRM_BY_EMAIL");
    public static final AccountDeletionResponse DELETION_IN_PROGRESS = new AccountDeletionResponse("DELETION_IN_PROGRESS");
    public static final AccountDeletionResponse DELETION_CANCELLED = new AccountDeletionResponse("DELETION_CANCELLED");
}
