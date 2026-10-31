package com.ltz.mail.infrastructure.config;

/** SMTP aktarım güvenliği. {@code NONE} yalnız geliştirmede (Mailpit); üretimde reddedilir. */
public enum SmtpSecurity {
    NONE,
    STARTTLS,
    SSL
}
