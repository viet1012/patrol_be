package com.example.patrol_be.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    private String account;
    private String oldPassword;
    private String newPassword;
}
