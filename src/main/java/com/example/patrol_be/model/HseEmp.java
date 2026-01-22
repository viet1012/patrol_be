package com.example.patrol_be.model;

import jakarta.persistence.*;

@Entity
@Table(name = "HSE_EmpID")
public class HseEmp {

    @Id
    @Column(name = "EmpID")
    private String empId;

    @Column(name = "EmpName")
    private String empName;

    @Column(name = "Plant")
    private String plant;

    @Column(name = "Dept")
    private String dept;

    @Column(name = "Note")
    private String note;

    // getters/setters
}
