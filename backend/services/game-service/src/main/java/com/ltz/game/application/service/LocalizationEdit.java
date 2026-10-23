package com.ltz.game.application.service;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.enums.MetadataDataState;
import java.util.List;
public record LocalizationEdit(String metadataLanguage, MetadataDataState dataState, List<Support> languageSupport,
                               boolean verified, String evidence) {
    public record Support(String languageCode, LanguageSupportStatus interfaceSupport,
                          LanguageSupportStatus audioSupport, LanguageSupportStatus subtitleSupport) { }
}
