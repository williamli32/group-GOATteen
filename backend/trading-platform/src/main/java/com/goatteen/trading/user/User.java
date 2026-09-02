package com.goatteen.trading.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "users")
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private String email;


    @Column(name = "password_hash", nullable = false)
    private String passwordHash;


    @Column(nullable = false)
    private boolean enabled = true;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }


    public String getEmail() {
        return email;
    }


    public String getPasswordHash() {
        return passwordHash;
    }


    public boolean isEnabled() {
        return enabled;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}