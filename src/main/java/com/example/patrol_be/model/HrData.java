package com.example.patrol_be.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "F2_HR_Data")
public class HrData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "No")
    private String no;

    @Column(name = "Code", unique = true, nullable = false)
    private String code;

    @Column(name = "Factory_New")
    private String factoryNew;

    @Column(name = "Fac")
    private String fac;

    @Column(name = "Dept")
    private String dept;

    @Column(name = "Section")
    private String section;

    @Column(name = "Line")
    private String line;

    @Column(name = "Group")
    private String group;

    @Column(name = "Date_of_resign")
    private LocalDate dateOfResign;

    @Column(name = "Name")
    private String name;

    @Column(name = "Date_of_start_working")
    private LocalDate dateOfStartWorking;

    @Column(name = "Newshift")
    private String newshift;

    @Column(name = "reg_date")
    private LocalDate regDate;

    // --- Constructor, getter và setter ---

    public HrData() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFactoryNew() {
        return factoryNew;
    }

    public void setFactoryNew(String factoryNew) {
        this.factoryNew = factoryNew;
    }

    public String getFac() {
        return fac;
    }

    public void setFac(String fac) {
        this.fac = fac;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public LocalDate getDateOfResign() {
        return dateOfResign;
    }

    public void setDateOfResign(LocalDate dateOfResign) {
        this.dateOfResign = dateOfResign;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateOfStartWorking() {
        return dateOfStartWorking;
    }

    public void setDateOfStartWorking(LocalDate dateOfStartWorking) {
        this.dateOfStartWorking = dateOfStartWorking;
    }

    public String getNewshift() {
        return newshift;
    }

    public void setNewshift(String newshift) {
        this.newshift = newshift;
    }

    public LocalDate getRegDate() {
        return regDate;
    }

    public void setRegDate(LocalDate regDate) {
        this.regDate = regDate;
    }
}
