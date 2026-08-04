package com.training.platform.users.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "MB_USER_PROFILES", uniqueConstraints =
        @UniqueConstraint(name = "UK_MB_PROFILE_USER", columnNames = "USER_ID"))
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_profile_sequence")
    @SequenceGenerator(name = "user_profile_sequence", sequenceName = "MB_USER_PROFILE_SEQ", allocationSize = 1)
    @Column(name = "PROFILE_ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false,
            foreignKey = @ForeignKey(name = "FK_MB_PROFILE_USER"))
    private User user;

    @Column(name = "FIRST_NAME", nullable = false, length = 80)
    private String firstName;

    @Column(name = "MIDDLE_NAME", length = 80)
    private String middleName;

    @Column(name = "LAST_NAME", nullable = false, length = 80)
    private String lastName;

    @Column(name = "PHONE_NUMBER", length = 20)
    private String phoneNumber;

    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;

    @Column(name = "ADDRESS_LINE_1", length = 150)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE_2", length = 150)
    private String addressLine2;

    @Column(name = "CITY", length = 80)
    private String city;

    @Column(name = "STATE_NAME", length = 80)
    private String state;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "COUNTRY_CODE", length = 2)
    private String countryCode;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

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
