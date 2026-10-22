package com.ltz.social.domain.enums;

/** Arkadaşlık isteği durumları (DB'de varchar + CHECK). PENDING dışındakiler son durumdur. */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
