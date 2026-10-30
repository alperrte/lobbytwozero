package com.ltz.library.infrastructure.persistence.repository;

import com.ltz.library.domain.entity.LibraryEntry;
import com.ltz.library.domain.enums.LibraryAction;
import com.ltz.library.domain.enums.LibraryFlag;
import com.ltz.library.domain.enums.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

    public List<LibraryEntry> findCollection(UUID userId, Boolean favorite, Boolean completed, SortDirection direction, int page, int size) {
        List<?> rows = bindFilters(entities.createNativeQuery("SELECT * FROM library_entries" + collectionWhere(favorite, completed)
                + " ORDER BY added_at " + direction.name() + ", id " + direction.name() + " LIMIT :size OFFSET :offset", LibraryEntry.class)
                .setParameter("size", size).setParameter("offset", (long) page * size), userId, favorite, completed).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public long countCollection(UUID userId, Boolean favorite, Boolean completed) {
        return ((Number) bindFilters(entities.createNativeQuery("SELECT count(*) FROM library_entries" + collectionWhere(favorite, completed)),
                userId, favorite, completed).getSingleResult()).longValue();
    }

    public List<LibraryEntry> collectionSnapshot(UUID userId, Boolean favorite, Boolean completed) {
        List<?> rows = bindFilters(entities.createNativeQuery("SELECT * FROM library_entries" + collectionWhere(favorite, completed), LibraryEntry.class),
                userId, favorite, completed).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public record Counts(long total, long favorite, long completed) { }

    public Counts statistics(UUID userId) {
        Object[] counts = (Object[]) entities.createNativeQuery("SELECT count(*), count(*) FILTER (WHERE favorite),"
                + " count(*) FILTER (WHERE completed) FROM library_entries WHERE user_id = :userId")
                .setParameter("userId", userId).getSingleResult();
        return new Counts(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(), ((Number) counts[2]).longValue());
    }

    private static String collectionWhere(Boolean favorite, Boolean completed) {
        return " WHERE user_id = :userId" + (favorite == null ? "" : " AND favorite = :favorite")
                + (completed == null ? "" : " AND completed = :completed");
    }

    private static Query bindFilters(Query query, UUID userId, Boolean favorite, Boolean completed) {
        query.setParameter("userId", userId);
        if (favorite != null) query.setParameter("favorite", favorite);
        if (completed != null) query.setParameter("completed", completed);
        return query;
    }

    public void eraseAccount(UUID userId) {
        entities.createNativeQuery("DELETE FROM library_entries WHERE user_id = :userId")
                .setParameter("userId", userId).executeUpdate();
    }

    public List<LibraryEntry> findSignals(UUID userId, int page, int size) {
        List<?> rows = entities.createNativeQuery("SELECT * FROM library_entries WHERE user_id = :userId"
                + " AND (favorite OR completed) ORDER BY added_at DESC, id DESC LIMIT :size OFFSET :offset", LibraryEntry.class)
                .setParameter("userId", userId).setParameter("size", size).setParameter("offset", (long) page * size).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public long countSignals(UUID userId) {
        return ((Number) entities.createNativeQuery("SELECT count(*) FROM library_entries WHERE user_id = :userId AND (favorite OR completed)")
                .setParameter("userId", userId).getSingleResult()).longValue();
    }
}
