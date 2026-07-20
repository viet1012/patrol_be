package com.example.patrol_be.dto;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateQrException.class)
	public ResponseEntity<?> handleDuplicateQr(
			DuplicateQrException exception
	) {
		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(
						Map.of(
								"status", 409,
								"code", "DUPLICATE_QR",
								"message", exception.getMessage()
						)
				);
	}

	@ExceptionHandler(InvalidQrException.class)
	public ResponseEntity<?> handleInvalidQr(
			InvalidQrException exception
	) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(
						Map.of(
								"status", 400,
								"code", "INVALID_QR",
								"message", exception.getMessage()
						)
				);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleUnknown(
			Exception exception
	) {
		exception.printStackTrace();

		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(
						Map.of(
								"status", 500,
								"code", "INTERNAL_SERVER_ERROR",
								"message", "Internal server error."
						)
				);
	}
}