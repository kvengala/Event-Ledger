package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    Optional<EventEntity> findByEventId(String eventId);

    List<EventEntity> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
