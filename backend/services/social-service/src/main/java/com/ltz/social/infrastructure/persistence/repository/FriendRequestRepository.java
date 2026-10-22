package com.ltz.social.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.social.domain.entity.FriendRequest;
import com.ltz.social.domain.enums.FriendRequestStatus;

import jakarta.persistence.LockModeType;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {

    /** Yanıt/iptal yarışlarını sıraya koymak için satır kilidiyle okur (ikinci işlem güncel durumu görür). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FriendRequest r where r.id = :id")
    Optional<FriendRequest> findByIdForUpdate(@Param("id") UUID id);

    /** İki kullanıcı arasındaki (yönden bağımsız) bekleyen istek; engelleme bunu kapatır. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from FriendRequest r
            where r.status = com.ltz.social.domain.enums.FriendRequestStatus.PENDING
              and ((r.senderId = :first and r.receiverId = :second)
                   or (r.senderId = :second and r.receiverId = :first))""")
    List<FriendRequest> findPendingBetweenForUpdate(@Param("first") UUID first, @Param("second") UUID second);

    boolean existsBySenderIdAndReceiverIdAndStatus(UUID senderId, UUID receiverId, FriendRequestStatus status);

    long countBySenderIdAndStatus(UUID senderId, FriendRequestStatus status);

    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDescIdDesc(UUID receiverId,
            FriendRequestStatus status, Limit limit);

    @Query("""
            select r from FriendRequest r
            where r.receiverId = :receiverId and r.status = :status
              and (r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :id))
            order by r.createdAt desc, r.id desc""")
    List<FriendRequest> findIncomingAfter(@Param("receiverId") UUID receiverId,
            @Param("status") FriendRequestStatus status, @Param("createdAt") Instant createdAt,
            @Param("id") UUID id, Limit limit);

    List<FriendRequest> findBySenderIdAndStatusOrderByCreatedAtDescIdDesc(UUID senderId,
            FriendRequestStatus status, Limit limit);

    @Query("""
            select r from FriendRequest r
            where r.senderId = :senderId and r.status = :status
              and (r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :id))
            order by r.createdAt desc, r.id desc""")
    List<FriendRequest> findOutgoingAfter(@Param("senderId") UUID senderId,
            @Param("status") FriendRequestStatus status, @Param("createdAt") Instant createdAt,
            @Param("id") UUID id, Limit limit);
}
