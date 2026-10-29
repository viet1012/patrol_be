package com.example.patrol_be.dto;


public class WorkflowConflictException extends RuntimeException {
	public WorkflowConflictException(String message) {
		super(message);
	}
}
