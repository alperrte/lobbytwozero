package com.ltz.community.infrastructure.persistence.repository;

import java.util.UUID;

/** Bir yorumun doğrudan, herkese açık yanıt sayısı (liste sayfası için toplu sorgu sonucu). */
public interface ReplyCount {

	UUID getParentId();

	long getTotal();
}
