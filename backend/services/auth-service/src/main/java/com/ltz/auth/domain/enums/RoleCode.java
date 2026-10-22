package com.ltz.auth.domain.enums;

/**
 * Kanonik rol kodları (docs/database.md §3.1). Kayıtta yalnız PLAYER atanır;
 * diğer roller yalnız yetkili rol yönetimi (AUTH-11) ile verilir.
 */
public enum RoleCode {
    PLAYER,
    CONTENT_CREATOR,
    GAME_DEVELOPER,
    MODERATOR,
    ADMIN
}
