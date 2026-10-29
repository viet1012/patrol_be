package com.example.patrol_be.dto;


public record ApiErrorResponse(
		int status,
		String message,
		String code
) {
}