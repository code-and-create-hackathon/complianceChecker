package com.compliancechecker.model;

/**
 * Coarse category of a compliance problem. Unlike problem texts, categories never quote the prompt.
 */
public enum ProblemCategory {
	CUSTOMER_DATA,
	ACCOUNT_DATA,
	INSIDER_INFO,
	CREDENTIALS,
	OTHER
}
