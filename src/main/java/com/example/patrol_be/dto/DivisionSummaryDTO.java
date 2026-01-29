package com.example.patrol_be.dto;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DivisionSummaryDTO {
    private String division;

    private long allTtl;
    private long allI;
    private long allII;
    private long allIII;
    private long allIV;
    private long allV;

    private long proDoneTtl;
    private long proDoneI;
    private long proDoneII;
    private long proDoneIII;
    private long proDoneIV;
    private long proDoneV;

    private long hseDoneTtl;
    private long hseDoneI;
    private long hseDoneII;
    private long hseDoneIII;
    private long hseDoneIV;
    private long hseDoneV;

    private long remainTtl;
    private long remainI;
    private long remainII;
    private long remainIII;
    private long remainIV;
    private long remainV;
}
