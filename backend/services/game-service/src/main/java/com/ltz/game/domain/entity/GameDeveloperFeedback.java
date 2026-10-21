package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
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
