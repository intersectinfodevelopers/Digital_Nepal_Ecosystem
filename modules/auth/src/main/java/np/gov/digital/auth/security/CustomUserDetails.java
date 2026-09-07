package np.gov.digital.auth.security;

import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.entity.User;
import np.gov.digital.platformaudit.audit.AuthenticatedActor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails, AuthenticatedActor {

    private final User user;

    public UUID getUserId() {
        return user.getId();
    }

    @Override
    public String getRole() {
        return user.getRole().name();
    }

    public UUID getWardId() {
        return user.getWardId();
    }

    public UUID getMunicipalityId() {
        return user.getMunicipalityId();
    }

    public UUID getProvinceId() {
        return user.getProvinceId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return Boolean.TRUE.equals(user.getAccountNonLocked());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }

    public User getUser() {
        return user;
    }
}
