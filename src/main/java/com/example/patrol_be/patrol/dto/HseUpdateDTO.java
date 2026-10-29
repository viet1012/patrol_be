package com.example.patrol_be.dto;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class HseUpdateDTO {

    /**
     * HSE judgement / result
     * Ví dụ: OK / NG
     */
    private String hseJudge;

    private String hseComment;

    private String hseUser;

    private String atStatus;
}
