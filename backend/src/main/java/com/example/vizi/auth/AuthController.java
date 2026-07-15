package com.example.vizi.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthUser register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    AuthUser me(Authentication authentication) {
        return authService.getByEmail(authentication.getName());
    }

    @PutMapping("/me")
    AuthUser updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        return authService.updateProfile(authentication.getName(), request);
    }

    @PutMapping("/me/email")
    AuthUser changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var updated = authService.changeEmail(authentication.getName(), request);
        // Email is the session principal — force re-login after change.
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
        SecurityContextHolder.clearContext();
        return updated;
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        authService.changePassword(authentication.getName(), request);
    }
}

record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 160) String fullName
) {
}

record UpdateProfileRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 40) String phone,
        @Size(max = 500) String address
) {
}

record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 320) String newEmail,
        @NotBlank @Size(min = 8, max = 72) String currentPassword
) {
}

record ChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}

record AuthUser(
        Long id,
        String email,
        String fullName,
        String role,
        String phone,
        String address
) {

    static AuthUser from(User user) {
        return new AuthUser(
                user.id(),
                user.email(),
                user.fullName(),
                user.role(),
                user.phone(),
                user.address()
        );
    }
}
