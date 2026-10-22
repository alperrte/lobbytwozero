package com.ltz.social.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.social.domain.entity.Friendship;
import com.ltz.social.domain.entity.FriendshipId;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {
}
