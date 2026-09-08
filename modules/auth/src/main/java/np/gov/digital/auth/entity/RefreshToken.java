package np.gov.digital.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // BUG FIX: was length = 512 (VARCHAR(512)) — the real RS256 JWT refresh
    // token JwtService generates routinely exceeds that, which failed every
    // login with a DataIntegrityViolationException on insert (see V32
    // migration). columnDefinition="TEXT" matches the widened column;
    // `length` is meaningless once columnDefinition is set explicitly.
    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime expiryDate;

    @Builder.Default
    private Boolean revoked = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
