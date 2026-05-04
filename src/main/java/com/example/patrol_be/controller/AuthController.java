package com.example.patrol_be.controller;

import com.example.patrol_be.constants.ErrorCode;
import com.example.patrol_be.dto.AuthRequest;
import com.example.patrol_be.dto.AuthResponse;
import com.example.patrol_be.dto.ChangePasswordRequest;
import com.example.patrol_be.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

	private final AuthService authService;

	////////////////////////////////////////////////////////////
	/// CHECK ACCOUNT
	////////////////////////////////////////////////////////////
	@GetMapping("/check-account-exists")
	public ResponseEntity<Boolean> checkAccountExists(@RequestParam String account) {
		return ResponseEntity.ok(authService.existsByAccount(account));
	}

	////////////////////////////////////////////////////////////
	/// REGISTER
	////////////////////////////////////////////////////////////
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {

		AuthResponse res = authService.register(request);

		if (!res.isSuccess()) {
			switch (res.getCode()) {

				case ErrorCode.ACCOUNT_EXISTS:
					return ResponseEntity.status(409).body(res);

				case ErrorCode.INVALID_DATA:
					return ResponseEntity.badRequest().body(res);

				default:
					return ResponseEntity.status(400).body(res);
			}
		}

		return ResponseEntity.ok(res);
	}

	////////////////////////////////////////////////////////////
	/// LOGIN
	////////////////////////////////////////////////////////////
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {

		AuthResponse res = authService.login(request);

		if (!res.isSuccess()) {
			switch (res.getCode()) {

				case ErrorCode.ACCOUNT_NOT_FOUND:
					return ResponseEntity.status(404).body(res);

				case ErrorCode.WRONG_PASSWORD:
					return ResponseEntity.status(401).body(res);

				case ErrorCode.INVALID_DATA:
					return ResponseEntity.badRequest().body(res);

				default:
					return ResponseEntity.status(400).body(res);
			}
		}

		return ResponseEntity.ok(res);
	}

	////////////////////////////////////////////////////////////
	/// CHANGE PASSWORD
	////////////////////////////////////////////////////////////
	@PostMapping("/change_password")
	public ResponseEntity<AuthResponse> changePassword(@RequestBody ChangePasswordRequest req) {

		AuthResponse res = authService.changePassword(req);

		if (!res.isSuccess()) {
			switch (res.getCode()) {

				case ErrorCode.ACCOUNT_NOT_FOUND:
					return ResponseEntity.status(404).body(res);

				case ErrorCode.WRONG_PASSWORD:
					return ResponseEntity.status(401).body(res);

				case ErrorCode.INVALID_DATA:
					return ResponseEntity.badRequest().body(res);

				default:
					return ResponseEntity.status(400).body(res);
			}
		}

		return ResponseEntity.ok(res);
	}


	@GetMapping("/export-password")
	public ResponseEntity<String> exportPassword(
			@RequestParam String account,
			@RequestParam String email
	) throws Exception {

		String path = authService.exportPasswordExcel(account, email);

		return ResponseEntity.ok("File saved at: " + path);
	}

}