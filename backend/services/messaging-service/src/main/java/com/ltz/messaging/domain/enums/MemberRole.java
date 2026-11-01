package com.ltz.messaging.domain.enums;

/**
 * Sohbet içindeki rol. Birebir sohbette iki taraf da {@code MEMBER}'dır. Grupta tek bir {@code OWNER} vardır;
 * {@code OWNER} ve {@code ADMIN} üye ekler/çıkarır (MSG-03, MSG-05), rolleri yalnız {@code OWNER} değiştirir.
 */
public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean managesMembers() {
        return this == OWNER || this == ADMIN;
    }

    /** OWNER herkesi (kendisi hariç) çıkarabilir; ADMIN yalnız MEMBER'ı. */
    public boolean canRemove(MemberRole target) {
        return this == OWNER || (this == ADMIN && target == MEMBER);
    }
}
