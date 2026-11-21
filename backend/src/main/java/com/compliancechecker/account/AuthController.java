package com.compliancechecker.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

	/** adminCode is only needed (and only checked) for the administrator. */
	public record LoginRequest(
			@NotBlank(message = "must not be empty")
			@Size(max = 100, message = "is too long (max. {max} characters)")
			String username,
			@Size(max = 200, message = "is too long (max. {max} characters)")
			String adminCode) {

		/** Never shows the admin code. */
		@Override
		public String toString() {
			return "LoginRequest[username=%s]".formatted(username);
		}
	}

	public record LoginResponse(String token, String username, Role role) {
	}

	public record DemoLoginRequest(
			@NotBlank(message = "must not be empty")
			@Size(max = 100, message = "is too long (max. {max} characters)")
			String key) {
	}

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PublicEndpoint
	@PostMapping("/api/auth/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		AuthService.Login login = authService.login(request.username(), request.adminCode());
		return new LoginResponse(login.token(), login.user().getUsername(), login.user().getRole());
	}

	/** QR demo: a key the presenter shows as a QR code (qr.html). Valid for 3 minutes. */
	@AdminOnly
	@PostMapping("/api/auth/demo-key")
	public AuthService.DemoKey demoKey() {
		return authService.createDemoKey();
	}

	/** QR demo: logs the scanner in as the guest account for 3 minutes. */
	@PublicEndpoint
	@PostMapping("/api/auth/demo-login")
	public LoginResponse demoLogin(@Valid @RequestBody DemoLoginRequest request) {
		AuthService.Login login = authService.demoLogin(request.key());
		return new LoginResponse(login.token(), login.user().getUsername(), login.user().getRole());
	}

	@PostMapping("/api/auth/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		authService.logout(AuthInterceptor.bearerToken(request));
		return ResponseEntity.noContent().build();
	}

	/** Who am I? Lets clients check whether a stored token is still valid. */
	@GetMapping("/api/auth/me")
	public UserView me(@RequestAttribute(AuthInterceptor.CURRENT_USER) AppUser user) {
		return UserView.of(user);
	}
}
