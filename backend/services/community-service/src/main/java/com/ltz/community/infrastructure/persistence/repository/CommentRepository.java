package com.ltz.community.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.community.domain.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, UUID>, JpaSpecificationExecutor<Comment> {

	/** Verilen üst yorumların doğrudan, herkese açık (VISIBLE) yanıt sayıları; sayfa başına tek sorgu. */
	@Query("""
			select c.parentCommentId as parentId, count(c) as total
			from Comment c
			where c.parentCommentId in :parentIds and c.status = com.ltz.community.domain.enums.CommentStatus.VISIBLE
			group by c.parentCommentId
			""")
	List<ReplyCount> countVisibleReplies(@Param("parentIds") Collection<UUID> parentIds);
}
