package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="game_developer_feedback") @Getter @Setter @NoArgsConstructor
public class GameDeveloperFeedback {
    @Id private UUID id=UUID.randomUUID();
    @Column(nullable=false) private UUID gameId;
    @Column(nullable=false) private UUID authorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private FeedbackType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private FeedbackStatus status=FeedbackStatus.OPEN;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=4000) private String body;
    @Version private long version;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
}
