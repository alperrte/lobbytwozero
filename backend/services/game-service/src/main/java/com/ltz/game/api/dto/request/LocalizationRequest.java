package com.ltz.game.api.dto.request;
import com.ltz.game.application.service.LocalizationEdit;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.enums.MetadataDataState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
/** verified=true, admin'in bu girdiyi ayrıca doğruladığını beyan eder; kaydetmek tek başına doğrulama değildir. */
public record LocalizationRequest(@Size(max=35) String metadataLanguage, MetadataDataState dataState,
        @Size(max=100) List<@NotNull @Valid Support> languageSupport, Boolean verified,
        @Size(max=1000) String evidence) {
    public record Support(@NotBlank @Size(max=35) String languageCode, LanguageSupportStatus interfaceSupport,
                          LanguageSupportStatus audioSupport, LanguageSupportStatus subtitleSupport) {
        LocalizationEdit.Support toEdit() { return new LocalizationEdit.Support(languageCode,interfaceSupport,audioSupport,subtitleSupport); }
    }
    public LocalizationEdit toEdit() {
        return new LocalizationEdit(metadataLanguage,dataState,
                languageSupport==null ? null : languageSupport.stream().map(Support::toEdit).toList(),Boolean.TRUE.equals(verified),evidence);
    }
}
