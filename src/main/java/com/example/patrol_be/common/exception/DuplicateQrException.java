package com.example.patrol_be.common.exception;


public class DuplicateQrException extends RuntimeException {
	public DuplicateQrException(String qrKey) {
		super("QR code '" + qrKey + "' already exists and has not been closed.");
	}
}