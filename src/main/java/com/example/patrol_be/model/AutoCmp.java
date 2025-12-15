package com.example.patrol_be.model;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "HSE_Patrol_Comment")

public class AutoCmp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer No;

    @Column(name = "InputText")
    private String inputText;

    @Column(name = "Note")
    private String note;

    @Column(name = "Sort_Order")
    private Integer sortOrder;

    @Column(name = "Countermeasure")
    private String countermeasure;

}
