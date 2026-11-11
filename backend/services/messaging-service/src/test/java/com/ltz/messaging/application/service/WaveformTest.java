package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/** Sesli mesaj dalga formu (MSG-21): tam 64 tam sayı, her biri 0–100, virgülle ayrılmış. */
class WaveformTest {

    @Test
    void altmisDortDegerOkunurVeListeyeDoner() {
        String raw = String.join(",", Collections.nCopies(63, "50")) + ",100";
        byte[] parsed = Waveform.parse(raw);

        assertThat(parsed).hasSize(64);
        List<Integer> values = Waveform.toList(parsed);
        assertThat(values).hasSize(64).startsWith(50).endsWith(100);
        assertThat(Waveform.parse(String.join(",", Collections.nCopies(64, "0")))).containsOnly(0);
    }

    @Test
    void verilmezseBostur() {
        assertThat(Waveform.parse(null)).isNull();
        assertThat(Waveform.toList(null)).isNull();
    }

    @Test
    void bicimDisiDalgaFormuReddedilir() {
        String sixtyThree = String.join(",", Collections.nCopies(63, "5"));
        for (String raw : List.of("", sixtyThree, sixtyThree + ",5,5", sixtyThree + ",101", sixtyThree + ",-1",
                sixtyThree + ", 5", sixtyThree + ",5,", sixtyThree + ",x", sixtyThree + ",0005",
                sixtyThree + ",99999999999")) {
            assertThatThrownBy(() -> Waveform.parse(raw)).as(raw).isInstanceOfSatisfying(MessagingException.class,
                    exception -> assertThat(exception.errorCode()).isEqualTo(MessagingErrorCode.INVALID_WAVEFORM));
        }
    }
}
