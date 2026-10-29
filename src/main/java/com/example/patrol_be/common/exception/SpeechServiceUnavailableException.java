package com.example.patrol_be.dto;

public class SpeechServiceUnavailableException extends RuntimeException {

	public static final String DEFAULT_MESSAGE =
			"Dịch vụ nhận diện giọng nói không khả dụng";

	public SpeechServiceUnavailableException(Throwable cause) {
		super(DEFAULT_MESSAGE, cause);
	}

	public SpeechServiceUnavailableException() {
		super(DEFAULT_MESSAGE);
	}
}
