package com.example.patrol_be.dto;


public class InvalidQrException extends RuntimeException {
	public InvalidQrException(String message) {
		super(message);
	}
}
