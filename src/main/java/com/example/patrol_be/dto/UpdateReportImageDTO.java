package com.example.patrol_be.dto;

import lombok.Data;

@Data
public class UpdateReportImageDTO {
    private Long reportId;
    private String oldImage;
    private String newImage;
}
