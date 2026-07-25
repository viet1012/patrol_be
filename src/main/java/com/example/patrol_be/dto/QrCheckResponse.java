package com.example.patrol_be.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCheckResponse {

	private String qrKey;

	/**
	 * QR đúng định dạng 1–5 chữ số.
	 */
	private boolean valid;

	/**
	 * QR chưa được sử dụng bởi report đang mở.
	 */
	private boolean available;

	/**
	 * QR đang tồn tại ở report chưa Closed.
	 */
	private boolean duplicate;

	private String message;
}