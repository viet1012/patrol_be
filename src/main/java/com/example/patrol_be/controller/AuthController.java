package com.example.patrol_be.controller;

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

    @GetMapping("/check-account-exists")
    public ResponseEntity<Boolean> checkAccountExists(@RequestParam String account) {
        boolean exists = authService.existsByAccount(account);
        return ResponseEntity.ok(exists);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/change_password")
    public AuthResponse changePassword(
            @RequestBody ChangePasswordRequest req) {
        return authService.changePassword(req);
    }
}
