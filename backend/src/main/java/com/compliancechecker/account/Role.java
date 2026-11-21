package com.compliancechecker.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** The administrator sets the compliance policy and manages users; workers only run checks against it. */
public enum Role {
	ADMIN,
	WORKER;

	@JsonValue
	public String jsonValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	@JsonCreator
	public static Role fromJson(String value) {
		return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
	}
}
