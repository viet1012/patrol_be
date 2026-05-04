package com.example.patrol_be.service;

import com.example.patrol_be.constants.ErrorCode;
import com.example.patrol_be.dto.AuthRequest;
import com.example.patrol_be.dto.AuthResponse;
import com.example.patrol_be.dto.ChangePasswordRequest;
import com.example.patrol_be.model.PatrolAccount;
import com.example.patrol_be.repository.HrDataRepository;
import com.example.patrol_be.repository.PatrolAccountRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PatrolAccountRepository repo;
    private final HrDataRepository hseEmpRepo;

    public boolean existsByAccount(String account) {
        return repo.existsByAccount(account);
    }

    ////////////////////////////////////////////////////////////
    /// REGISTER
    ////////////////////////////////////////////////////////////
    public AuthResponse register(AuthRequest req) {

        final String account = req.getAccount() == null
                ? ""
                : req.getAccount().trim();

        if (account.isEmpty()) {
            return new AuthResponse(
                    false,
                    ErrorCode.INVALID_DATA,
                    "Account is required."
            );
        }

        // 👉 optional check HR
//        if (!hseEmpRepo.existsByEmpId(account)) {
//            return new AuthResponse(
//                    false,
//                    ErrorCode.INVALID_DATA,
//                    "Employee ID not found."
//            );
//        }

        if (repo.existsByAccount(account)) {
            return new AuthResponse(
                    false,
                    ErrorCode.ACCOUNT_EXISTS,
                    "Account already exists."
            );
        }

        PatrolAccount acc = new PatrolAccount();
        acc.setAccount(account);
        acc.setPass(req.getPassword()); // 🔐 TODO: hash later
        acc.setNewDT(LocalDateTime.now());

        repo.save(acc);

        return new AuthResponse(
                true,
                ErrorCode.SUCCESS,
                "Registration successful."
        );
    }

    ////////////////////////////////////////////////////////////
    /// LOGIN
    ////////////////////////////////////////////////////////////
    public AuthResponse login(AuthRequest req) {

        final String account = req.getAccount() == null
                ? ""
                : req.getAccount().trim();

        if (account.isEmpty()) {
            return new AuthResponse(
                    false,
                    ErrorCode.INVALID_DATA,
                    "Account is required."
            );
        }

        PatrolAccount acc = repo.findByAccount(account)
                .orElse(null);

        if (acc == null) {
            return new AuthResponse(
                    false,
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found."
            );
        }

        if (!acc.getPass().equals(req.getPassword())) {
            return new AuthResponse(
                    false,
                    ErrorCode.WRONG_PASSWORD,
                    "Incorrect password."
            );
        }

        acc.setLastLogin(LocalDateTime.now());
        repo.save(acc);

        return new AuthResponse(
                true,
                ErrorCode.SUCCESS,
                "Login successful."
        );
    }

    ////////////////////////////////////////////////////////////
    /// CHANGE PASSWORD
    ////////////////////////////////////////////////////////////
    public AuthResponse changePassword(ChangePasswordRequest req) {

        final String account = req.getAccount() == null
                ? ""
                : req.getAccount().trim();

        if (account.isEmpty()) {
            return new AuthResponse(
                    false,
                    ErrorCode.INVALID_DATA,
                    "Account is required."
            );
        }

        PatrolAccount acc = repo.findByAccount(account)
                .orElse(null);

        if (acc == null) {
            return new AuthResponse(
                    false,
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found."
            );
        }

        if (!acc.getPass().equals(req.getOldPassword())) {
            return new AuthResponse(
                    false,
                    ErrorCode.WRONG_PASSWORD,
                    "Old password is incorrect."
            );
        }

        acc.setPass(req.getNewPassword());
        acc.setUpdDT(LocalDateTime.now());

        repo.save(acc);

        return new AuthResponse(
                true,
                ErrorCode.SUCCESS,
                "Password changed successfully."
        );
    }


    public String exportPasswordExcel(String account, String email) throws Exception {

        if (account == null || account.trim().isEmpty()) {
            throw new RuntimeException("Account is required");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        // 👉 tìm account
        PatrolAccount acc = repo.findByAccount(account).orElse(null);

        if (acc == null) {
            throw new RuntimeException("Account not found");
        }

        ////////////////////////////////////////////////////////////
        // 👉 TẠO FILE PATH
        ////////////////////////////////////////////////////////////

        String folderPath = "C:\\Users\\it.production\\OneDrive - MISUMI Group Inc\\IT Program - 18. Computer Record\\17. HeatPressAlert\\";

        // tạo folder nếu chưa có
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // đặt tên file (tránh bị ghi đè)
        String time = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String fileName = "account_" + account + "_" + time + ".xlsx";

        String fullPath = folderPath + fileName;

        ////////////////////////////////////////////////////////////
        // 👉 TẠO EXCEL
        ////////////////////////////////////////////////////////////

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Accounts");

        // HEADER
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("MSNV");
        header.createCell(1).setCellValue("Password");
        header.createCell(2).setCellValue("Email");

        // DATA
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(acc.getAccount());
        row.createCell(1).setCellValue(acc.getPass());
        row.createCell(2).setCellValue(email);

        // auto size
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);

        ////////////////////////////////////////////////////////////
        // 👉 GHI FILE RA Ổ ĐĨA
        ////////////////////////////////////////////////////////////

        FileOutputStream fileOut = new FileOutputStream(fullPath);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();

        ////////////////////////////////////////////////////////////
        // 👉 RETURN PATH
        ////////////////////////////////////////////////////////////

        return fullPath;
    }
}