package com.example.patrol_be.dto;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DivisionSummaryDTO {
    private String division;

    private double allTtl;
    private double allI;
    private double allII;
    private double allIII;
    private double allIV;
    private double allV;

    private double proDoneTtl;
    private double proDoneI;
    private double proDoneII;
    private double proDoneIII;
    private double proDoneIV;
    private double proDoneV;

    private double hseDoneTtl;
    private double hseDoneI;
    private double hseDoneII;
    private double hseDoneIII;
    private double hseDoneIV;
    private double hseDoneV;

    private double remainTtl;
    private double remainI;
    private double remainII;
    private double remainIII;
    private double remainIV;
    private double remainV;
}
