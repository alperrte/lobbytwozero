package com.ltz.content.domain.exception;

/** Resmî duyuruyu yalnız yönetici oluşturabilir (CONT-04/CONT-18); normal kullanıcıya bu yetki verilmez. */
public class OfficialAnnouncementForbiddenException extends RuntimeException {

	public OfficialAnnouncementForbiddenException() {
		super("Only administrators can create official announcements.");
	}
}
