package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.TermKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Entity @Table(name="catalog_terms") @Getter @Setter @NoArgsConstructor
public class CatalogTerm {
    @Id private UUID id = UUID.randomUUID();
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private TermKind kind;
    @Column(nullable=false, length=64) private String code;
    @Column(length=128) private String nameTr;
    @Column(length=128) private String nameEn;
    @Version private long version;
}
