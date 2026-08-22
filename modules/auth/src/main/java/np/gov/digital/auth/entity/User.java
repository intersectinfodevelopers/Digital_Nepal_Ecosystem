package np.gov.digital.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import np.gov.digital.auth.enums.Role;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private UUID wardId;

    private UUID municipalityId;

    private UUID provinceId;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean accountNonLocked = true;

    @Builder.Default
    private Integer failedAttempts = 0;

    private LocalDateTime lockTime;

    // Set TRUE when an account is created by an admin (see AdminController)
    // with a system-generated temporary password. Login still succeeds with
    // the temp password, but the client must be forced to the
    // change-password flow before anything else — enforced in AuthService.
    @Builder.Default
    private Boolean passwordResetRequired = false;

    // The account that created this one (Local Body Admin who created a
    // Ward Admin, Central Admin who created a Province/Local Body Admin,
    // etc.). Null only for the very first, manually-seeded Central Admin.
    private UUID createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}