package com.ltz.content.domain.exception;

/** İşlem yalnız yönetici (ADMIN) yetkisiyle yapılabilir (ör. içeriği öne çıkarma). */
public class AdministratorRequiredException extends RuntimeException {

	public AdministratorRequiredException() {
		super("Only administrators can perform this action.");
	}
}
