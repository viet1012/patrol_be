package com.example.patrol_be.service;

import com.example.patrol_be.dto.AuthRequest;
import com.example.patrol_be.dto.AuthResponse;
import com.example.patrol_be.dto.ChangePasswordRequest;
import com.example.patrol_be.model.PatrolAccount;
import com.example.patrol_be.repository.HrDataRepository;
import com.example.patrol_be.repository.PatrolAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.*;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PatrolAccountRepository repo;
    private final HrDataRepository hseEmpRepo;

    public boolean existsByAccount(String account) {
        return repo.existsByAccount(account);
    }

    // REGISTER
    public AuthResponse register(AuthRequest req) {
        final String account = req.getAccount() == null ? "" : req.getAccount().trim();

        // 1) EmpID ph?i t?n t?i trong HSE_EmpID
        if (!hseEmpRepo.existsByEmpId(account)) {
            return new AuthResponse(false, "EmpID not found in SPC");
        }

        // 2) Account không du?c trùng trong PatrolAccount
        if (repo.existsByAccount(account)) {
            return new AuthResponse(false, "Account already exists");
        }

        PatrolAccount acc = new PatrolAccount();
        acc.setAccount(account);
        acc.setPass(req.getPassword()); // ?? nên hash b?ng BCrypt
        acc.setNewDT(LocalDateTime.now());
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

        acc.setLastLogin(LocalDateTime.now());
        repo.save(acc);

        return new AuthResponse(true, "Login success");
    }

    // 🔑 CHANGE PASSWORD
    public AuthResponse changePassword(ChangePasswordRequest req) {

        PatrolAccount acc = repo.findByAccount(req.getAccount())
                .orElse(null);

        if (acc == null) {
            return new AuthResponse(false, "Account not found");
        }

        if (!acc.getPass().equals(req.getOldPassword())) {
            return new AuthResponse(false, "Old password is incorrect");
        }

        acc.setPass(req.getNewPassword());
        acc.setUpdDT(LocalDateTime.now()); // ✅ thời gian update

        repo.save(acc);

        return new AuthResponse(true, "Password changed successfully");
    }
}
