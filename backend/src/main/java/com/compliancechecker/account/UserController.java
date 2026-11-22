package com.compliancechecker.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User management, administrator only. There is no self-registration, and this endpoint only creates workers: the one
 * administrator comes from the configuration (AdminBootstrap).
 */
@AdminOnly
@RestController
public class UserController {

	public record CreateUserRequest(
			@NotBlank(message = "must not be empty")
			@Pattern(regexp = UserService.USERNAME_PATTERN,
					message = "must be 2-40 characters: lower-case letters, digits, '.', '_' or '-'")
			String username) {
	}

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/api/users")
	public List<UserView> list() {
		return userService.list().stream().map(UserView::of).toList();
	}

	@PostMapping("/api/users")
	@ResponseStatus(HttpStatus.CREATED)
	public UserView create(@Valid @RequestBody CreateUserRequest request) {
		return UserView.of(userService.create(request.username(), Role.WORKER));
	}
}
