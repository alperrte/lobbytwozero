package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import jakarta.persistence.*;
import lombok.*;
@Embeddable @Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class GameLanguageSupport {
    @Column(name="language_code", nullable=false, length=35) private String languageCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private LanguageSupportStatus interfaceSupport = LanguageSupportStatus.UNKNOWN;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private LanguageSupportStatus audioSupport = LanguageSupportStatus.UNKNOWN;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private LanguageSupportStatus subtitleSupport = LanguageSupportStatus.UNKNOWN;
}
