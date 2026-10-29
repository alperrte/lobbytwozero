package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.DeveloperContentManagementResponse;
import com.ltz.game.api.dto.response.DeveloperContentResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.DeveloperContentBase;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameDeveloperContent;
import com.ltz.game.domain.entity.GameDeveloperMedia;
import com.ltz.game.domain.entity.GameUpdateNote;
import com.ltz.game.domain.enums.ContentStatus;
import com.ltz.game.domain.enums.DeveloperMediaType;
import com.ltz.game.domain.exception.ContentNotFoundException;
import com.ltz.game.domain.exception.GameConflictException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperContentRepository;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperMediaRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import com.ltz.game.infrastructure.persistence.repository.GameUpdateNoteRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geliştirici içerikleri (GAME-13): açıklama, medya bağlantıları ve güncelleme notları. İçerik, katalog verisinden ayrı
 * tablolarda tutulur ve yazarı bellidir. Yayın öncesi onay yoktur; moderatör/admin gizleyebilir (SECURITY.md §5).
 * Gizlenen içerik sahibi tarafından düzenlenemez, yalnız silinebilir.
 */
@Service @RequiredArgsConstructor
public class DeveloperContentService {
    static final int MAX_IMAGES = 10;
    static final int MAX_VIDEOS = 5;
    static final int MAX_DEMOS = 3;
    static final int MAX_NOTES = 100;
    static final int PUBLIC_NOTES = 50;
    private static final String INVALID_NOTE = "INVALID_NOTE";
    private final GameAccessPolicy access;
    private final GameRepository games;
    private final GameDeveloperContentRepository contents;
    private final GameDeveloperMediaRepository media;
    private final GameUpdateNoteRepository notes;
    private final MediaUrlPolicy urls;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DeveloperContentResponse publicView(UUID gameId) {
        games.findById(gameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        var description = contents.findById(gameId).filter(DeveloperContentBase::isVisible)
                .map(content -> new DeveloperContentResponse.Description(content.getDescription(), content.getUpdatedAt())).orElse(null);
        var mediaItems = media.findByGameIdAndStatusOrderByCreatedAtAscIdAsc(gameId, ContentStatus.VISIBLE).stream()
                .map(item -> new DeveloperContentResponse.Media(item.getId(), item.getType(), item.getUrl(), item.getAltText(), item.getCreatedAt())).toList();
        var noteItems = notes.findByGameIdAndStatusOrderByCreatedAtDescIdAsc(gameId, ContentStatus.VISIBLE, PageRequest.of(0, PUBLIC_NOTES)).stream()
                .map(note -> new DeveloperContentResponse.UpdateNote(note.getId(), note.getTitle(), note.getBody(), note.getCreatedAt(), note.getUpdatedAt())).toList();
        return new DeveloperContentResponse(gameId, "DEVELOPER", description, mediaItems, noteItems);
    }

    @Transactional(readOnly = true)
    public DeveloperContentManagementResponse management(Actor actor, UUID gameId) {
        access.requireDeveloper(actor, gameId, false);
        var description = contents.findById(gameId).map(content -> new DeveloperContentManagementResponse.Description(
                content.getDescription(), content.getStatus(), content.getVersion(), content.getModerationReason(), content.getUpdatedAt())).orElse(null);
        var mediaItems = media.findByGameIdAndStatusNotOrderByCreatedAtAscIdAsc(gameId, ContentStatus.DELETED).stream()
                .map(item -> new DeveloperContentManagementResponse.Media(item.getId(), item.getType(), item.getUrl(), item.getAltText(),
                        item.getStatus(), item.getModerationReason(), item.getCreatedAt())).toList();
        var noteItems = notes.findByGameIdAndStatusNotOrderByCreatedAtDescIdAsc(gameId, ContentStatus.DELETED, PageRequest.of(0, MAX_NOTES)).stream()
                .map(note -> management(note)).toList();
        return new DeveloperContentManagementResponse(gameId, description, mediaItems, noteItems);
    }

    @Transactional
    public DeveloperContentManagementResponse.Description putDescription(Actor actor, UUID gameId, String text, Long version) {
        access.requireDeveloper(actor, gameId, true);
        String description = RequestRules.text(text, 8000, "INVALID_DESCRIPTION");
        var content = contents.findById(gameId).orElse(null);
        if (content == null) {
            if (version != null) throw new GameConflictException("VERSION_CONFLICT");
            content = new GameDeveloperContent();
            content.setGameId(gameId);
            content.setCreatedAt(clock.instant());
        } else {
            if (content.getStatus() != ContentStatus.VISIBLE) throw new GameConflictException("CONTENT_HIDDEN");
            if (version == null || version != content.getVersion()) throw new GameConflictException("VERSION_CONFLICT");
        }
        content.setDescription(description);
        content.setAuthorId(actor.userId());
        content.setUpdatedAt(clock.instant());
        content = contents.saveAndFlush(content);
        return new DeveloperContentManagementResponse.Description(content.getDescription(), content.getStatus(), content.getVersion(),
                content.getModerationReason(), content.getUpdatedAt());
    }

    @Transactional
    public DeveloperContentManagementResponse.Media addMedia(Actor actor, UUID gameId, DeveloperMediaType type, String url, String altText) {
        access.requireDeveloper(actor, gameId, true);
        String validated = urls.validate(type, url);
        String alt = RequestRules.optionalText(altText, 200, "INVALID_ALT_TEXT");
        long existing = media.countByGameIdAndTypeAndStatusNot(gameId, type, ContentStatus.DELETED);
        int limit = switch (type) { case IMAGE -> MAX_IMAGES; case VIDEO -> MAX_VIDEOS; case DEMO -> MAX_DEMOS; };
        if (existing >= limit) throw new GameConflictException("MEDIA_LIMIT_REACHED");
        var item = new GameDeveloperMedia();
        item.setGameId(gameId);
        item.setAuthorId(actor.userId());
        item.setType(type);
        item.setUrl(validated);
        item.setAltText(alt);
        item.setCreatedAt(clock.instant());
        item.setUpdatedAt(clock.instant());
        item = media.saveAndFlush(item);
        return new DeveloperContentManagementResponse.Media(item.getId(), item.getType(), item.getUrl(), item.getAltText(),
                item.getStatus(), item.getModerationReason(), item.getCreatedAt());
    }

    @Transactional
    public void deleteMedia(Actor actor, UUID gameId, UUID mediaId) {
        access.requireDeveloper(actor, gameId, true);
        var item = media.findByIdAndGameId(mediaId, gameId).filter(found -> found.getStatus() != ContentStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("MEDIA_NOT_FOUND"));
        item.setStatus(ContentStatus.DELETED);
        item.setUpdatedAt(clock.instant());
        media.saveAndFlush(item);
    }

    @Transactional
    public DeveloperContentManagementResponse.UpdateNote addNote(Actor actor, UUID gameId, String title, String body) {
        access.requireDeveloper(actor, gameId, true);
        String validTitle = RequestRules.text(title, 120, INVALID_NOTE);
        String validBody = RequestRules.text(body, 4000, INVALID_NOTE);
        if (notes.countByGameIdAndStatusNot(gameId, ContentStatus.DELETED) >= MAX_NOTES) throw new GameConflictException("NOTE_LIMIT_REACHED");
        var note = new GameUpdateNote();
        note.setGameId(gameId);
        note.setAuthorId(actor.userId());
        note.setTitle(validTitle);
        note.setBody(validBody);
        note.setCreatedAt(clock.instant());
        note.setUpdatedAt(clock.instant());
        return management(notes.saveAndFlush(note));
    }

    @Transactional
    public DeveloperContentManagementResponse.UpdateNote updateNote(Actor actor, UUID gameId, UUID noteId, String title, String body, Long version) {
        access.requireDeveloper(actor, gameId, true);
        String validTitle = RequestRules.text(title, 120, INVALID_NOTE);
        String validBody = RequestRules.text(body, 4000, INVALID_NOTE);
        var note = notes.findByIdAndGameId(noteId, gameId).filter(found -> found.getStatus() != ContentStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("NOTE_NOT_FOUND"));
        if (note.getStatus() != ContentStatus.VISIBLE) throw new GameConflictException("CONTENT_HIDDEN");
        if (version == null) throw new InvalidGameRequestException("VERSION_REQUIRED");
        if (version != note.getVersion()) throw new GameConflictException("VERSION_CONFLICT");
        note.setTitle(validTitle);
        note.setBody(validBody);
        note.setAuthorId(actor.userId());
        note.setUpdatedAt(clock.instant());
        return management(notes.saveAndFlush(note));
    }

    @Transactional
    public void deleteNote(Actor actor, UUID gameId, UUID noteId) {
        access.requireDeveloper(actor, gameId, true);
        var note = notes.findByIdAndGameId(noteId, gameId).filter(found -> found.getStatus() != ContentStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("NOTE_NOT_FOUND"));
        note.setStatus(ContentStatus.DELETED);
        note.setUpdatedAt(clock.instant());
        notes.saveAndFlush(note);
    }

    private static DeveloperContentManagementResponse.UpdateNote management(GameUpdateNote note) {
        return new DeveloperContentManagementResponse.UpdateNote(note.getId(), note.getTitle(), note.getBody(), note.getStatus(),
                note.getVersion(), note.getModerationReason(), note.getCreatedAt(), note.getUpdatedAt());
    }
}
