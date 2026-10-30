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
    /** Sorgular yalnız derleme zamanı sabitleridir; filtreler ve kimlikler bağlı parametredir, SQL birleştirilmez. */
    private static final String COLLECTION_FILTER = " WHERE user_id = :userId AND (:filterFavorite = false OR favorite = :favorite)"
            + " AND (:filterCompleted = false OR completed = :completed)";
    private static final String COLLECTION_ALL = "SELECT * FROM library_entries" + COLLECTION_FILTER;
    private static final String COLLECTION_COUNT = "SELECT count(*) FROM library_entries" + COLLECTION_FILTER;
    private static final String COLLECTION_ASC = COLLECTION_ALL + " ORDER BY added_at ASC, id ASC LIMIT :size OFFSET :offset";
    private static final String COLLECTION_DESC = COLLECTION_ALL + " ORDER BY added_at DESC, id DESC LIMIT :size OFFSET :offset";
    private static final String CLEAR_FAVORITE = "UPDATE library_entries SET favorite = false, updated_at = clock_timestamp(),"
            + " version = version + 1 WHERE user_id = :userId AND game_id = :gameId AND favorite = true";
    private static final String CLEAR_COMPLETED = "UPDATE library_entries SET completed = false, updated_at = clock_timestamp(),"
            + " version = version + 1 WHERE user_id = :userId AND game_id = :gameId AND completed = true";

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
        entities.createNativeQuery(flag == LibraryFlag.FAVORITE ? CLEAR_FAVORITE : CLEAR_COMPLETED)
                .setParameter("userId", userId).setParameter("gameId", gameId).executeUpdate();
    }

    public List<LibraryEntry> findFlagged(UUID userId, LibraryFlag flag, int page, int size) {
        return findCollection(userId, flag == LibraryFlag.FAVORITE ? Boolean.TRUE : null,
                flag == LibraryFlag.COMPLETED ? Boolean.TRUE : null, SortDirection.DESC, page, size);
    }

    public long countFlagged(UUID userId, LibraryFlag flag) {
        return countCollection(userId, flag == LibraryFlag.FAVORITE ? Boolean.TRUE : null,
                flag == LibraryFlag.COMPLETED ? Boolean.TRUE : null);
    }

    public List<LibraryEntry> findCollection(UUID userId, Boolean favorite, Boolean completed, SortDirection direction, int page, int size) {
        List<?> rows = bindFilters(entities.createNativeQuery(direction == SortDirection.ASC ? COLLECTION_ASC : COLLECTION_DESC, LibraryEntry.class)
                .setParameter("size", size).setParameter("offset", (long) page * size), userId, favorite, completed).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public long countCollection(UUID userId, Boolean favorite, Boolean completed) {
        return ((Number) bindFilters(entities.createNativeQuery(COLLECTION_COUNT), userId, favorite, completed)
                .getSingleResult()).longValue();
    }

    public List<LibraryEntry> collectionSnapshot(UUID userId, Boolean favorite, Boolean completed) {
        List<?> rows = bindFilters(entities.createNativeQuery(COLLECTION_ALL, LibraryEntry.class), userId, favorite, completed).getResultList();
        return rows.stream().map(LibraryEntry.class::cast).toList();
    }

    public record Counts(long total, long favorite, long completed) { }

    public Counts statistics(UUID userId) {
        Object[] counts = (Object[]) entities.createNativeQuery("SELECT count(*), count(*) FILTER (WHERE favorite),"
                + " count(*) FILTER (WHERE completed) FROM library_entries WHERE user_id = :userId")
                .setParameter("userId", userId).getSingleResult();
        return new Counts(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(), ((Number) counts[2]).longValue());
    }

    private static Query bindFilters(Query query, UUID userId, Boolean favorite, Boolean completed) {
        return query.setParameter("userId", userId)
                .setParameter("filterFavorite", favorite != null).setParameter("favorite", Boolean.TRUE.equals(favorite))
                .setParameter("filterCompleted", completed != null).setParameter("completed", Boolean.TRUE.equals(completed));
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
