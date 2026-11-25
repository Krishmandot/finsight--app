package com.finsight.domain.entity;

import com.finsight.domain.enums.CategoryKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A spending/income category. System categories ({@code userId == null}) are shared by everyone and
 * read-only; custom categories belong to exactly one user.
 */
@Entity
@Table(name = "categories")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "parent_name", length = 100)
    private String parentName;

    @Column(name = "icon_code", length = 50)
    private String iconCode;

    @Column(name = "is_system_defined", nullable = false)
    private boolean systemDefined;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "category_kind", nullable = false, length = 10)
    private CategoryKind kind = CategoryKind.EXPENSE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isOwnedBy(UUID candidateUserId) {
        return userId != null && userId.equals(candidateUserId);
    }

    public boolean isVisibleTo(UUID candidateUserId) {
        return userId == null || userId.equals(candidateUserId);
    }
}
