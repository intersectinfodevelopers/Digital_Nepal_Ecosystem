package np.gov.digital.auth.service;

import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.entity.RefreshToken;
import np.gov.digital.auth.entity.User;
import np.gov.digital.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    // BUG FIX (load-test finding, Day 16): deleteByUser is a derived delete
    // query and calls entityManager.remove() under the hood, which requires
    // an active transaction. Without @Transactional here, the FIRST login
    // for any user works (nothing to delete yet), but every login after
    // that throws jakarta.persistence.TransactionRequiredException — i.e.
    // every real user's second login was broken. See docs/load-test-report.md
    // Section 6 for how this was found.
    @Transactional
    public RefreshToken createRefreshToken(User user) {

        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(jwtService.generateRefreshToken(user))
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String token) {

        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new RuntimeException("Refresh token revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        return refreshToken;
    }

    public void revokeToken(RefreshToken refreshToken) {

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }
}