package com.example.patrol_be.dto;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
}
