package com.example.patrol_be.dto;


import lombok.Data;

import java.util.List;

@Data
public class PatrolEditDTO {
    private String comment;
    private String countermeasure;
    private String editUser;

    // danh sách ảnh cần xóa (tên file)
    private List<String> deleteImages;

    private String grp;
    private String plant;
    private String division;
    private String area;
    private String machine;
    private String pic;
}
