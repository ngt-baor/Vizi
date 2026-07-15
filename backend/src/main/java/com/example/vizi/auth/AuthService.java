package com.example.vizi.auth;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    AuthUser register(RegisterRequest request) {
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        try {
            var user = userRepository.save(new User(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.fullName().trim()
            ));
            return AuthUser.from(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", exception);
        }
    }

    @Transactional(readOnly = true)
    AuthUser getByEmail(String email) {
        return AuthUser.from(requireUser(email));
    }

    @Transactional(readOnly = true)
    public User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    @Transactional(readOnly = true)
    public User requireAdmin(String email) {
        var user = requireUser(email);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return user;
    }

    @Transactional
    AuthUser updateProfile(String currentEmail, UpdateProfileRequest request) {
        var user = requireUser(currentEmail);
        var fullName = request.fullName() == null ? user.fullName() : request.fullName().trim();
        if (fullName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required");
        }
        var phone = normalizeOptional(request.phone());
        var address = normalizeOptional(request.address());
        user.updateProfile(fullName, phone, address);
        return AuthUser.from(userRepository.save(user));
    }

    @Transactional
    AuthUser changeEmail(String currentEmail, ChangeEmailRequest request) {
        var user = requireUser(currentEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        var nextEmail = normalizeEmail(request.newEmail());
        if (nextEmail.equalsIgnoreCase(user.email())) {
            return AuthUser.from(user);
        }
        if (userRepository.existsByEmailIgnoreCase(nextEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        user.changeEmail(nextEmail);
        return AuthUser.from(userRepository.save(user));
    }

    @Transactional
    void changePassword(String currentEmail, ChangePasswordRequest request) {
        var user = requireUser(currentEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
