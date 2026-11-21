package com.compliancechecker.model;

import java.util.Map;

/**
 * Body of every error response, see "Error responses" in API.md. All texts are fixed server-side wording and never contain submitted
 * content.
 *
 * @param error       stable machine-readable code, e.g. {@code INVALID_REQUEST}
 * @param message     human-readable summary
 * @param fieldErrors field name to problem, empty if not applicable
 * @param requestId   id for correlating with the server log
 */
public record ErrorResponse(String error, String message, Map<String, String> fieldErrors, String requestId) {
}
