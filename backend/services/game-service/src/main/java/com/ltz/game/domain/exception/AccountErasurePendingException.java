package com.ltz.game.domain.exception;

/** Kişisel veri kalırken Auth'a silme onayı verilmesini önler. */
public class AccountErasurePendingException extends RuntimeException {
    public AccountErasurePendingException() { super("Account erasure is incomplete"); }
}
