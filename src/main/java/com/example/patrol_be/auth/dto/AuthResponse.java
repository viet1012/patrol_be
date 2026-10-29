package com.example.patrol_be.auth.dto;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private boolean success;
    private String code;
    private String message;
}