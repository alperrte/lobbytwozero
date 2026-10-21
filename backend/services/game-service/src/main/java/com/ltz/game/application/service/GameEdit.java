package com.ltz.game.application.service;

import com.ltz.game.domain.enums.GameType;
import java.time.LocalDate;
import java.util.Set;

/**
 * Yönetici düzenleme komutu. {@code null} alan "değiştirme" demektir; boş metin/boş küme açıkça temizler.
 * Böylece kısmi bir istek sağlayıcıdan gelen alanları yanlışlıkla silmez ve gereksiz manuel kilit üretmez.
 */
public record GameEdit(String title, String description, LocalDate releaseDate, Boolean earlyAccess, GameType gameType,
        Set<String> languages, Set<String> developers, Set<String> publishers, Set<String> genres,
        Set<String> categories, Set<String> tags, Set<String> platforms, String coverUrl) { }
