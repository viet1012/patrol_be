package com.example.patrol_be.common.exception;


public class WorkflowConflictException extends RuntimeException {
	public WorkflowConflictException(String message) {
		super(message);
	}
}
