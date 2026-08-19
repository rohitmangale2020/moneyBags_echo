package com.training.platform.users.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "MB_USERS", uniqueConstraints = {
        @UniqueConstraint(name = "UK_MB_USERS_USERNAME", columnNames = "USERNAME"),
        @UniqueConstraint(name = "UK_MB_USERS_EMAIL", columnNames = "EMAIL")
})
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_sequence")
    @SequenceGenerator(name = "user_sequence", sequenceName = "MB_USER_SEQ", allocationSize = 1)
    @Column(name = "USER_ID")
    private Long id;

    @Column(name = "USERNAME", nullable = false, length = 50)
    private String username;

    @Column(name = "EMAIL", nullable = false, length = 254)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "PASSWORD_CHANGE_REQUIRED", nullable = false)
    private boolean passwordChangeRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "USER_ROLE", nullable = false, length = 50)
    private String role;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserProfile profile;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "ROW_VERSION", nullable = false)
    private Long version;

    public void attachProfile(UserProfile newProfile) {
        if (profile != null) {
            profile.setUser(null);
        }
        profile = newProfile;
        if (newProfile != null) {
            newProfile.setUser(this);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
