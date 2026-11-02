package com.example.patrol_be.common.exception;

/** User lacks the required Group × Action permission (PermissionService). */
public class PermissionDeniedException extends RuntimeException {

	public PermissionDeniedException(String message) {
		super(message);
	}
}
