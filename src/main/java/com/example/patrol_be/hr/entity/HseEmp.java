package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "HSE_EmpID")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "Role")
    private String role;

    // getters/setters
}
