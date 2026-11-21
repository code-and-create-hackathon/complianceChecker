package com.compliancechecker.account;

import com.compliancechecker.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before every /api endpoint (registered in {@code WebConfig}). Deny by default: an endpoint needs a valid
 * {@code Authorization: Bearer <token>} unless it is marked {@link PublicEndpoint}, and the admin role if it is marked
 * {@link AdminOnly}. The logged-in user is available to controllers as request attribute {@link #CURRENT_USER}.
 *
 * <p>TODO(security): when moving to real authentication (Spring Security + Cognito), this class is what gets replaced.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

	public static final String CURRENT_USER = "currentUser";

	private static final String BEARER = "Bearer ";

	private final AuthService authService;

	public AuthInterceptor(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// CORS preflights and Spring's own handlers (e.g. 404) are not endpoints of ours.
		if (!(handler instanceof HandlerMethod method) || method.hasMethodAnnotation(PublicEndpoint.class)) {
			return true;
		}
		AppUser user = authService.userFor(bearerToken(request)).orElseThrow(() -> new ApiException(
				HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in."));
		boolean adminOnly = method.hasMethodAnnotation(AdminOnly.class)
				|| method.getBeanType().isAnnotationPresent(AdminOnly.class);
		if (adminOnly && !user.isAdmin()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the administrator can do this.");
		}
		request.setAttribute(CURRENT_USER, user);
		return true;
	}

	/** The token from {@code Authorization: Bearer <token>}, or null. */
	public static String bearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		return header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()).strip() : null;
	}
}
