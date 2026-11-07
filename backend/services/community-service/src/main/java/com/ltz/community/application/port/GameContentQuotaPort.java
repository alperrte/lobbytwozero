package com.ltz.community.application.port;

import java.time.Instant;

import com.ltz.community.domain.entity.Post;

/** Türlü içerik kotasını eşzamanlı isteklerde de atomik uygular. */
public interface GameContentQuotaPort {

	Post saveWithinQuota(Post post, Instant since, int maximum);
}
