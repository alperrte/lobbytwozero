package com.ltz.user.application.service;

/**
 * Profil görselinin dosya adları. URL'ye çevirmek API katmanının işidir. GIF'te {@code smallFile}, {@code largeFile} ile
 * aynıdır (tek dosya).
 */
public record MediaView(String smallFile, String largeFile, boolean animated) {
}
