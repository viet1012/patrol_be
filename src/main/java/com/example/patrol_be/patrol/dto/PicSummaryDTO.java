package com.example.patrol_be.patrol.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PicSummaryDTO {
    private String pic;

    private Integer allTtl;
    private Integer allOk;
    private Integer allNg;
    private Integer allNy;
    private Integer allNyPct; // số 0..100

    private Integer facATtl;
    private Integer facAOk;
    private Integer facANg;
    private Integer facANy;
    private Integer facANyPct;

    private Integer facBTtl;
    private Integer facBOk;
    private Integer facBNg;
    private Integer facBNy;
    private Integer facBNyPct;

    private Integer facCTtl;
    private Integer facCOk;
    private Integer facCNg;
    private Integer facCNy;
    private Integer facCNyPct;

    private Integer outsideTtl;
    private Integer outsideOk;
    private Integer outsideNg;
    private Integer outsideNy;
    private Integer outsideNyPct;
}
