package com.compliancechecker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApiCheckResponse(@JsonProperty("isSafe") boolean isSafe, List<Finding> problems,
		String improvedVersion) { }
