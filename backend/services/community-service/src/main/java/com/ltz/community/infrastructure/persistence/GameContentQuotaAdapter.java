package com.ltz.community.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.community.application.port.GameContentQuotaPort;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.exception.TooManyGameContentException;
import com.ltz.community.infrastructure.persistence.repository.PostRepository;

/** Oyun doğrulaması bittikten sonra kısa bir DB transaction'ında kullanıcı kotası ve kayıt atomik yürür. */
@Component
public class GameContentQuotaAdapter implements GameContentQuotaPort {

	private final JdbcTemplate jdbc;
	private final PostRepository postRepository;

	public GameContentQuotaAdapter(JdbcTemplate jdbc, PostRepository postRepository) {
		this.jdbc = jdbc;
		this.postRepository = postRepository;
	}

	@Override
	@Transactional
	public Post saveWithinQuota(Post post, Instant since, int maximum) {
		UUID authorId = post.getAuthorId();
		// Transaction boyunca aynı yazarın tüm oluşturma isteklerini sıraya sokar; başka yazarlar bağımsız kalır.
		long lockKey = authorId.getMostSignificantBits() ^ authorId.getLeastSignificantBits();
		jdbc.query("select pg_advisory_xact_lock(?)", statement -> statement.setLong(1, lockKey), result -> { });
		if (postRepository.countByAuthorIdAndContentTypeIsNotNullAndCreatedAtAfter(authorId, since) >= maximum) {
			throw new TooManyGameContentException();
		}
		return postRepository.saveAndFlush(post);
	}
}
