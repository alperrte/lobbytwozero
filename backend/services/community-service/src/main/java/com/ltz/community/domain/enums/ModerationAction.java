package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * admin-service'in içeriğe uygulattığı karar. HIDE: gizle; RESTORE: gizlemeyi kaldır; REMOVE: kalıcı kaldır
 * (soft delete, geri alınamaz); DISMISS: raporu reddet (içerik değişmez).
 */
public enum ModerationAction {
	HIDE,
	RESTORE,
	REMOVE,
	DISMISS;

	@JsonCreator
	public static ModerationAction fromJson(String value) {
		return StrictEnums.exact(ModerationAction.class, value);
	}
}
