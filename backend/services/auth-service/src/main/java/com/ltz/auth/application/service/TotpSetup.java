package com.ltz.auth.application.service;

/** Kurulum anında bir kez gösterilen TOTP secret'ı (Base32) ve otpauth URI'si (QR kod için). */
public record TotpSetup(String secret, String otpauthUri) {

    @Override
    public String toString() {
        return "TotpSetup[secret=***, otpauthUri=***]";
    }
}
