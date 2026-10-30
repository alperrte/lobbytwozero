package com.ltz.user.domain.enums;

/**
 * Misafirin (girişsiz) profil görünümü (K5). {@code BASIC}: yalnız ad, avatar, kapak. {@code DETAILED}: ek olarak
 * biyografi gibi profil alanları ve görünürlüğü {@code PUBLIC} olan bölümler.
 */
public enum GuestAccess {
    BASIC,
    DETAILED
}
