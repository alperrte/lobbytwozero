package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.TermKind;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="catalog_terms") @Getter @Setter @NoArgsConstructor
public class CatalogTerm {
    @Id private UUID id = UUID.randomUUID();
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private TermKind kind;
    @Column(nullable=false, length=64) private String code;
    @Column(length=128) private String nameTr;
    @Column(length=128) private String nameEn;
    @Version private long version;
}
