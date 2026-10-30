package com.ltz.user.domain.enums;

/** İzleyicinin profil sahibine göre konumu. Engel varsa izleyici profili hiç göremez (ilişki hesaplanmaz). */
public enum ViewerRelation {
    OWNER,
    FRIEND,
    MEMBER,
    GUEST
}
