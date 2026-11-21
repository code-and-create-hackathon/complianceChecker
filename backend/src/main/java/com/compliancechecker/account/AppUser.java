package com.compliancechecker.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A user account. Deliberately has no password column: see {@link AuthService}.
 * TODO(security): no credentials are stored; real authentication comes from the company's identity provider later.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue
	private Long id;

	/** Normalised, see {@link UserService#normalize}. */
	@Column(nullable = false, unique = true, length = UserService.MAX_USERNAME_LENGTH)
	private String username;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Role role;

	@Column(nullable = false)
	private Instant createdAt;

	protected AppUser() {
		// for JPA
	}

	public AppUser(String username, Role role, Instant createdAt) {
		this.username = username;
		this.role = role;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
