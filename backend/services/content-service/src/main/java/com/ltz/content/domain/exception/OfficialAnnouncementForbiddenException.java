package com.ltz.content.domain.exception;

/** Resmî duyuruyu yalnız yönetici oluşturabilir ve yönetebilir (CONT-04/CONT-18); normal kullanıcıya bu yetki verilmez. */
public class OfficialAnnouncementForbiddenException extends RuntimeException {

	public OfficialAnnouncementForbiddenException() {
		super("Only administrators can create official announcements.");
	}

	private OfficialAnnouncementForbiddenException(String message) {
		super(message);
	}

	/** Mevcut bir resmî duyuruyu değiştirme/silme/kaldırma girişimi (rol sonradan alınmış olsa bile yalnız yönetici). */
	public static OfficialAnnouncementForbiddenException forManagement() {
		return new OfficialAnnouncementForbiddenException("Only administrators can manage official announcements.");
	}
}
