package com.ltz.community.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** VISIBLE: herkese açık; HIDDEN: moderatör tarafından gizlenmiş (forum ve konuları herkesten gizlenir). */
public enum ForumStatus {
	VISIBLE,
	HIDDEN;

	@JsonCreator
	public static ForumStatus fromJson(String value) {
		return StrictEnums.exact(ForumStatus.class, value);
	}
}
