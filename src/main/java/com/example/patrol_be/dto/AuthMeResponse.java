package com.example.patrol_be.dto;


import lombok.*;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthMeResponse {
    private String empId;
    private String empName;
    private String plant;
    private String role;
    private Map<String, Map<String, Boolean>> permissions;
}
