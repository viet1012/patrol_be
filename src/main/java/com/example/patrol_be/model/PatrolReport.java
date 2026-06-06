package com.example.patrol_be.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "F2_Patrol_Report")
@Data
public class PatrolReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String type;
	private Integer stt;
	private String grp;
	private String plant;
	private String division;
	private String area;
	private String machine;

	private String riskFreq;
	private String riskProb;
	private String riskSev;
	private String riskTotal;
	private String pic;

	private LocalDate dueDate;

	@Column(name = "due_date_update_count")
	private Integer dueDateUpdateCount;

	@Column(name = "due_date_updated_by")
	private String dueDateUpdatedBy;

	@Column(name = "due_date_updated_at")
	private LocalDate dueDateUpdatedAt;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String comment;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String countermeasure;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String checkInfo;

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String imageNames;

	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String patrol_user;

	/// PATROL_EDIT
	private LocalDateTime edit_date;
	private String edit_user;


	/// PATROL_AFTER
	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String at_imageNames;
	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String at_comment;
	private LocalDateTime at_date;
	private String at_status;
	private String at_user;
	private String at_assign;
	private String at_user_update_assign;
	private String at_user_update_pic;
	private LocalDateTime at_user_update_assign_date;
	private LocalDateTime at_user_update_pic_date;

	/// HSE_CHECK
	private String hse_judge;
	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String hse_imageNames;
	@Column(columnDefinition = "NVARCHAR(MAX)")
	private String hse_comment;
	private LocalDateTime hse_date;
	private String hse_user;

	private String qr_key;
	private String qr_scan_sts;
}
