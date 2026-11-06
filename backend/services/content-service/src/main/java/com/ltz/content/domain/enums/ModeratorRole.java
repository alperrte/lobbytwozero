package com.ltz.content.domain.enums;

/**
 * Moderasyon kararını veren kullanıcının, admin-service tarafından doğrulanmış rolü (olay yükünde taşınır). Content-service
 * bu bilgiye yalnız geçerli bir servis token'ıyla gelen olayda güvenir; içerik türüne göre yeterliliğini ayrıca denetler.
 */
public enum ModeratorRole {
	MODERATOR,
	ADMIN
}
