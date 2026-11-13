package com.ltz.lfp.domain.enums;

/**
 * İlanın oynanacağı platform (LFP-03). Sabit listedir: game-service'in platform verisi Steam kaynaklı olduğundan çoğu
 * oyunda konsolları bilmez; oyunun kayıtlı platformlarına göre doğrulamak konsol ilanlarını haksız yere reddederdi.
 * DB'de {@code varchar} + {@code CHECK} olarak tutulur.
 */
public enum Platform {
    PC,
    PLAYSTATION,
    XBOX,
    NINTENDO_SWITCH,
    MOBILE,
    OTHER
}
