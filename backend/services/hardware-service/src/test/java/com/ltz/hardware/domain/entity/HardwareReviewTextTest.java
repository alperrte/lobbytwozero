package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.exception.HardwareConflictException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HardwareReviewTextTest {
    @Test
    void normalizesPlainTextAndDetectsRepeatedContentAcrossSpellingVariants() {
        assertThat(HardwareReviewText.clean("  Good\r\nreview\ttext  ", 5000)).isEqualTo("Good\nreview\ttext");
        assertThat(HardwareReviewText.fingerprint(" GOOD\nReview "))
                .isEqualTo(HardwareReviewText.fingerprint("good review"));
        assertThat(HardwareReviewText.hash("synthetic")).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "<script>", "＜script＞", "text\u0000", "text\u0007"})
    void rejectsUnsafeRawAndNormalizedText(String body) {
        assertThatIllegalArgumentException().isThrownBy(() -> HardwareReviewText.clean(body, 5000));
    }

    @Test
    void enforcesRawAndNormalizedLengthAndDomainRating() {
        var expanded = "ﬁ".repeat(2600);
        var oversized = "x".repeat(5001);
        var product = UUID.randomUUID();
        var owner = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> HardwareReviewText.clean(expanded, 5000));
        assertThatIllegalArgumentException().isThrownBy(() -> HardwareReviewText.clean(oversized, 5000));
        assertThatIllegalArgumentException().isThrownBy(() -> HardwareReviewText.clean(null, 5000));
        assertThatIllegalArgumentException().isThrownBy(() -> new HardwareReview(product, owner, "body", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new HardwareReview(product, owner, "body", 6));
        var review = new HardwareReview(product, owner, "body", null);
        assertThatThrownBy(() -> review.requireVersion(0)).isInstanceOf(HardwareConflictException.class);
        review.delete();
        assertThatThrownBy(review::delete).isInstanceOf(HardwareConflictException.class);
        assertThatThrownBy(() -> review.update("other", 3)).isInstanceOf(HardwareConflictException.class);
    }
}
