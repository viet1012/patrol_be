package com.example.patrol_be.common.exception;


public record ApiErrorResponse(
		int status,
		String message,
		String code
) {
}