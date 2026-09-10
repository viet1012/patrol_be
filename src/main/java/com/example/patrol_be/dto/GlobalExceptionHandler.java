package com.example.patrol_be.dto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// ============================================================
	// DUPLICATE QR
	// ============================================================
	@ExceptionHandler(DuplicateQrException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateQr(
			DuplicateQrException exception
	) {
		log.warn(
				"Duplicate QR: {}",
				exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(
						new ApiErrorResponse(
								409,
								exception.getMessage(),
								"DUPLICATE_QR"
						)
				);
	}


	// ============================================================
	// INVALID QR
	// ============================================================
	@ExceptionHandler(InvalidQrException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidQr(
			InvalidQrException exception
	) {
		log.warn(
				"Invalid QR: {}",
				exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(
						new ApiErrorResponse(
								400,
								exception.getMessage(),
								"INVALID_QR"
						)
				);
	}


	// ============================================================
	// BAD REQUEST
	// ============================================================
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
			IllegalArgumentException exception
	) {
		log.warn(
				"Bad request: {}",
				exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(
						new ApiErrorResponse(
								400,
								exception.getMessage(),
								"BAD_REQUEST"
						)
				);
	}


	// ============================================================
	// UNKNOWN / INTERNAL SERVER ERROR
	// ============================================================
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleException(
			Exception exception
	) {

		log.error(
				"========== UNHANDLED API ERROR ==========",
				exception
		);

		String message =
				exception.getMessage() != null
						&& !exception.getMessage().isBlank()
						? exception.getMessage()
						: exception.getClass().getSimpleName();

		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(
						new ApiErrorResponse(
								500,
								message,
								"INTERNAL_SERVER_ERROR"
						)
				);
	}
}