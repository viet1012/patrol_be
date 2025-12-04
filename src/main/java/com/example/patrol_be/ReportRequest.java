package com.example.patrol_be;


import lombok.Data;

@Data
public class ReportRequest {
    private String division;
    private String group;
    private String machine;
    private String comment;
    private String reason1;
    private String reason2;
    private String imageUrl;
}
