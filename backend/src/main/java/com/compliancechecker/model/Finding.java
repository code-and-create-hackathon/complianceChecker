package com.compliancechecker.model;

public record Finding(String ruleId, String severity, String message, String location) { }
