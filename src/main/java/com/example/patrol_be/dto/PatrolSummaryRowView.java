package com.example.patrol_be.dto;

public interface PatrolSummaryRowView {
	String getFac();
	String getPic();

	Long getBeforeTtl();
	Long getBeforeI();
	Long getBeforeII();
	Long getBeforeIII();
	Long getBeforeIV();
	Long getBeforeV();

	Long getFinishedTtl();
	Long getFinishedI();
	Long getFinishedII();
	Long getFinishedIII();
	Long getFinishedIV();
	Long getFinishedV();

	Long getRemainTtl();
	Long getRemainI();
	Long getRemainII();
	Long getRemainIII();
	Long getRemainIV();
	Long getRemainV();

	Long getRecheckAllTtl();

	Long getRecheckOkTtl();
	Long getRecheckOkI();
	Long getRecheckOkII();
	Long getRecheckOkIII();
	Long getRecheckOkIV();
	Long getRecheckOkV();

	Long getRecheckNgTtl();
	Long getRecheckNgI();
	Long getRecheckNgII();
	Long getRecheckNgIII();
	Long getRecheckNgIV();
	Long getRecheckNgV();
}