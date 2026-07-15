package com.example.vizi.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(length = 40)
    private String phone;

    @Column(length = 500)
    private String address;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName) {
        this(email, passwordHash, fullName, "USER");
    }

    public User(String email, String passwordHash, String fullName, String role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role == null || role.isBlank() ? "USER" : role.trim().toUpperCase();
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public String role() {
        return role;
    }

    public String phone() {
        return phone;
    }

    public String address() {
        return address;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    void updateProfile(String fullName, String phone, String address) {
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
    }

    void changeEmail(String email) {
        this.email = email;
    }

    void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
