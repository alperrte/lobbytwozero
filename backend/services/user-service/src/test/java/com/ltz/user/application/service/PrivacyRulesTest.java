package com.ltz.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;
import com.ltz.user.domain.enums.ViewerRelation;

/** Tek gizlilik kuralının tam matrisi: 4 izleyici × 3 görünürlük × 2 misafir izni. */
class PrivacyRulesTest {

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            "OWNER,  PUBLIC,  BASIC,    true",
            "OWNER,  FRIENDS, BASIC,    true",
            "OWNER,  PRIVATE, BASIC,    true",
            "OWNER,  PRIVATE, DETAILED, true",
            "FRIEND, PUBLIC,  BASIC,    true",
            "FRIEND, FRIENDS, BASIC,    true",
            "FRIEND, PRIVATE, BASIC,    false",
            "FRIEND, PRIVATE, DETAILED, false",
            "MEMBER, PUBLIC,  BASIC,    true",
            "MEMBER, FRIENDS, BASIC,    false",
            "MEMBER, PRIVATE, BASIC,    false",
            "MEMBER, FRIENDS, DETAILED, false",
            "GUEST,  PUBLIC,  BASIC,    false",
            "GUEST,  PUBLIC,  DETAILED, true",
            "GUEST,  FRIENDS, DETAILED, false",
            "GUEST,  PRIVATE, DETAILED, false"
    })
    void bolumGorunurlugu(ViewerRelation relation, Visibility visibility, GuestAccess guest, boolean expected) {
        assertThat(PrivacyRules.allows(relation, visibility, guest)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "OWNER,  BASIC,    true",
            "FRIEND, BASIC,    true",
            "MEMBER, BASIC,    true",
            "GUEST,  BASIC,    false",
            "GUEST,  DETAILED, true"
    })
    void profilAyrintilari(ViewerRelation relation, GuestAccess guest, boolean expected) {
        assertThat(PrivacyRules.showsDetails(relation, guest)).isEqualTo(expected);
    }
}
