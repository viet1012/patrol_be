package com.example.patrol_be.service;

import com.example.patrol_be.dto.AuthRequest;
import com.example.patrol_be.dto.AuthResponse;
import com.example.patrol_be.model.PatrolAccount;
import com.example.patrol_be.repository.PatrolAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PatrolAccountRepository repo;

    // REGISTER
    public AuthResponse register(AuthRequest req) {

        if (repo.existsByAccount(req.getAccount())) {
            return new AuthResponse(false, "Account already exists");
        }

        PatrolAccount acc = new PatrolAccount();
        acc.setAccount(req.getAccount());
        acc.setPass(req.getPassword()); // 🔥 demo đơn giản (chưa hash)

        repo.save(acc);

        return new AuthResponse(true, "Register success");
    }

    // LOGIN
    public AuthResponse login(AuthRequest req) {

        PatrolAccount acc = repo.findByAccount(req.getAccount())
                .orElse(null);

        if (acc == null) {
            return new AuthResponse(false, "Account not found");
        }

        if (!acc.getPass().equals(req.getPassword())) {
            return new AuthResponse(false, "Invalid password");
        }

        return new AuthResponse(true, "Login success");
    }
}
