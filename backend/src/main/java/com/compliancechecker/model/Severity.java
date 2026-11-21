package com.compliancechecker.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Severity of a problem as in the compliance file (API.md). Only {@link #LOW} is a notice that does not block. */
public enum Severity {
	CRITICAL,
	HIGH,
	MEDIUM,
	LOW;

	public boolean isBlocking() {
		return this != LOW;
	}

	@JsonValue
	public String jsonValue() {
		return name().toLowerCase(Locale.ROOT);
	}
}
