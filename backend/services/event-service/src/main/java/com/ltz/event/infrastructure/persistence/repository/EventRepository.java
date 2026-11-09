package com.ltz.event.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.event.domain.entity.Event;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
