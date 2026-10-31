package com.ltz.mail.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dil başına e-posta şablonu (MAIL-08/09). Gövde yalnız içerik parçasıdır; logo, düzen ve altbilgi
 * {@code mail/layout.html} içindedir. Şablonlar Flyway seed'iyle gelir, çalışma zamanında değişmez.
 */
@Entity
@Table(name = "mail_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailTemplate {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 48)
    private String code;

    @Column(name = "locale", nullable = false, updatable = false, length = 2)
    private String locale;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "body_text", nullable = false)
    private String bodyText;

    @Column(name = "version", nullable = false, updatable = false)
    private int templateVersion;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
