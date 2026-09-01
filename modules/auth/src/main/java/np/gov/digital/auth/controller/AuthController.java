package np.gov.digital.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import np.gov.digital.auth.dto.*;
import np.gov.digital.auth.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Login, token refresh, logout, and the current user's profile")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Log in",
            description = "Public endpoint. Exchanges a username/password for a JWT access token and a refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; tokens returned"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Refresh access token",
            description = "Public endpoint. Exchanges a valid refresh token for a new access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @RequestBody @Valid RefreshTokenRequest request) {

        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @Operation(
            summary = "Log out",
            description = "Invalidates the caller's refresh token. Requires a valid access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody @Valid LogoutRequest request) {

        authService.logout(request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the authenticated user's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            Authentication authentication) {

        return ResponseEntity.ok(
                authService.me(authentication)
        );
    }
}
