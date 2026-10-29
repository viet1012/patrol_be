package com.example.patrol_be.auth.dto;

import lombok.*;

@Getter
@Setter
public class AuthRequest {
    private String account;
    private String password;
}
