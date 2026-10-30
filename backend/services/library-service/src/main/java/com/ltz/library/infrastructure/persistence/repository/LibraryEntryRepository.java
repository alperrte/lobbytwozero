package com.ltz.library.infrastructure.persistence.repository;

import com.ltz.library.domain.entity.LibraryEntry;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.domain.enums.LibraryFlag;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class LibraryEntryRepository {
    private final EntityManager entities;

    public LibraryEntryRepository(EntityManager entities) { this.entities = entities; }

    /** UNIQUE kilidi aynı kullanıcı/oyun eklemelerini sıralar; ön kontrol yarışı yoktur. */
    public LibraryEntry write(UUID userId, UUID gameId, LibraryAction action) {
        return (LibraryEntry) entities.createNativeQuery("""
                INSERT INTO library_entries (id, user_id, game_id, favorite, completed)
                VALUES (:id, :userId, :gameId, :favorite, :completed)
                ON CONFLICT (user_id, game_id) DO UPDATE SET
                    favorite = library_entries.favorite OR EXCLUDED.favorite,
                    completed = library_entries.completed OR EXCLUDED.completed,
                    updated_at = CASE WHEN (EXCLUDED.favorite AND NOT library_entries.favorite)
                                        OR (EXCLUDED.completed AND NOT library_entries.completed)
                                 THEN clock_timestamp() ELSE library_entries.updated_at END,
                    version = library_entries.version + CASE WHEN (EXCLUDED.favorite AND NOT library_entries.favorite)
                                        OR (EXCLUDED.completed AND NOT library_entries.completed) THEN 1 ELSE 0 END
                RETURNING *
                """, LibraryEntry.class).setParameter("id", UUID.randomUUID())
                .setParameter("userId", userId).setParameter("gameId", gameId)
                .setParameter("favorite", action == LibraryAction.FAVORITE)
                .setParameter("completed", action == LibraryAction.COMPLETE).getSingleResult();
    }

    public void remove(UUID userId, UUID gameId) {
        entities.createNativeQuery("DELETE FROM library_entries WHERE user_id = :userId AND game_id = :gameId")
                .setParameter("userId", userId).setParameter("gameId", gameId).executeUpdate();
    }

    public void clearFlag(UUID userId, UUID gameId, LibraryFlag flag) {
        String column = flag.column();
        entities.createNativeQuery("UPDATE library_entries SET " + column
                + " = false, updated_at = clock_timestamp(), version = version + 1"
                + " WHERE user_id = :userId AND game_id = :gameId AND " + column + " = true")
                .setParameter("userId", userId).setParameter("gameId", gameId).executeUpdate();
    }

    public List<LibraryEntry> findFlagged(UUID userId, LibraryFlag flag, int page, int size) {
        List<?> rows = entities.createNativeQuery("SELECT * FROM library_entries WHERE user_id = :userId AND "
                + flag.column() + " = true ORDER BY added_at DESC, id DESC LIMIT :size OFFSET :offset", LibraryEntry.class)
                .setParameter("userId", userId).setParameter("size", size)
                .setParameter("offset", (long) page * size).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public long countFlagged(UUID userId, LibraryFlag flag) {
        return ((Number) entities.createNativeQuery("SELECT count(*) FROM library_entries WHERE user_id = :userId AND "
                + flag.column() + " = true").setParameter("userId", userId).getSingleResult()).longValue();
    }

    public void eraseAccount(UUID userId) {
        entities.createNativeQuery("DELETE FROM library_entries WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();
    }
}
