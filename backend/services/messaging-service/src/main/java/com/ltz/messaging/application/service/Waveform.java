package com.ltz.messaging.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/**
 * Sesli mesajın dalga formu (MSG-21): istemcinin kayıt sırasında ürettiği 64 değerlik genlik özeti, her değer 0–100.
 * Sunucu sesi çözmediği için hesaplayamaz; yalnız çizim içindir. Biçim katıdır (yalnız virgül ve 1–3 haneli sayılar);
 * 64 sayı serbest metin taşıyamaz. DB'de 64 baytlık {@code bytea} olarak saklanır.
 */
public final class Waveform {

    public static final int SIZE = 64;
    static final int MAX_VALUE = 100;
    private static final Pattern FORMAT = Pattern.compile("(?:0|[1-9]\\d{0,2})(?:,(?:0|[1-9]\\d{0,2})){63}");

    private Waveform() {
    }

    /** Virgülle ayrılmış 64 değer → 64 bayt; {@code null} → {@code null}. */
    public static byte[] parse(String raw) {
        if (raw == null) {
            return null;
        }
        if (!FORMAT.matcher(raw).matches()) {
            throw new MessagingException(MessagingErrorCode.INVALID_WAVEFORM);
        }
        String[] parts = raw.split(",");
        byte[] values = new byte[SIZE];
        for (int index = 0; index < SIZE; index++) {
            int value = Integer.parseInt(parts[index]);
            if (value > MAX_VALUE) {
                throw new MessagingException(MessagingErrorCode.INVALID_WAVEFORM);
            }
            values[index] = (byte) value;
        }
        return values;
    }

    /** Saklanan baytlar → 0–100 değer listesi; {@code null} → {@code null}. */
    public static List<Integer> toList(byte[] stored) {
        if (stored == null) {
            return null;
        }
        List<Integer> values = new ArrayList<>(stored.length);
        for (byte value : stored) {
            values.add(value & 0xFF);
        }
        return List.copyOf(values);
    }
}
