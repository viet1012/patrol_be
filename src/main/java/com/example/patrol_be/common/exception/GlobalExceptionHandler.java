package com.example.patrol_be.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// ============================================================
	// DUPLICATE QR
	// ============================================================
	@ExceptionHandler(DuplicateQrException.class)
	public ResponseEntity<ApiErrorResponse> handleDuplicateQr(
			DuplicateQrException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Duplicate QR: {}",
				requestLine(request),
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
	// WORKFLOW CONFLICT (sai thứ tự Before → After → HSE)
	// ============================================================
	@ExceptionHandler(WorkflowConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleWorkflowConflict(
			WorkflowConflictException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Workflow conflict: {}",
				requestLine(request),
				exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.CONFLICT)
				.body(
						new ApiErrorResponse(
								409,
								exception.getMessage(),
								"WORKFLOW_CONFLICT"
						)
				);
	}


	// ============================================================
	// INVALID QR
	// ============================================================
	@ExceptionHandler(InvalidQrException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidQr(
			InvalidQrException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Invalid QR: {}",
				requestLine(request),
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
	// STT SERVICE UNAVAILABLE (lỗi / timeout)
	// ============================================================
	@ExceptionHandler(SpeechServiceUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleSpeechUnavailable(
			SpeechServiceUnavailableException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Speech service unavailable: {}",
				requestLine(request),
				exception.getCause() != null
						? exception.getCause().toString()
						: exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(
						new ApiErrorResponse(
								503,
								exception.getMessage(),
								"SPEECH_UNAVAILABLE"
						)
				);
	}


	// ============================================================
	// PERMISSION DENIED (ma trận quyền Group × Action)
	// ============================================================
	@ExceptionHandler(PermissionDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handlePermissionDenied(
			PermissionDeniedException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Permission denied: {}",
				requestLine(request),
				exception.getMessage()
		);

		return ResponseEntity
				.status(HttpStatus.FORBIDDEN)
				.body(
						new ApiErrorResponse(
								403,
								exception.getMessage(),
								"PERMISSION_DENIED"
						)
				);
	}


	// ============================================================
	// MISSING / WRONG TYPE REQUEST PARAMETER (param, multipart part)
	// ============================================================
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParameter(
			MissingServletRequestParameterException exception,
			HttpServletRequest request
	) {
		return badRequestParameter("Thiếu tham số " + exception.getParameterName(), request);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingPart(
			MissingServletRequestPartException exception,
			HttpServletRequest request
	) {
		return badRequestParameter("Thiếu tham số " + exception.getRequestPartName(), request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request
	) {
		return badRequestParameter("Sai kiểu tham số " + exception.getName(), request);
	}

	/** 400 BAD_REQUEST, one WARN line (no stack trace). */
	private ResponseEntity<ApiErrorResponse> badRequestParameter(
			String message,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Bad request: {}",
				requestLine(request),
				message
		);

		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(
						new ApiErrorResponse(
								400,
								message,
								"BAD_REQUEST"
						)
				);
	}


	// ============================================================
	// BAD REQUEST
	// ============================================================
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
			IllegalArgumentException exception,
			HttpServletRequest request
	) {
		log.warn(
				"[{}] Bad request: {}",
				requestLine(request),
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
			Exception exception,
			HttpServletRequest request
	) {

		log.error(
				"========== UNHANDLED API ERROR [{}] ==========",
				requestLine(request),
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

	/** "METHOD /uri" of the failed request. */
	private static String requestLine(HttpServletRequest request) {
		return request.getMethod() + " " + request.getRequestURI();
	}
}
