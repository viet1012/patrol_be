package com.example.patrol_be.service;

import com.example.patrol_be.dto.DivisionSummaryDTO;
import com.example.patrol_be.dto.PatrolReportDTO;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatrolReportExportExcelService {

	private final PatrolReportService patrolReportService;

	private static final String IMAGE_BASE_URL =
			"http://192.168.122.16:8002/images/";

	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private static final DateTimeFormatter DATE_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/*
	 * Kích thước ảnh thumbnail.
	 */
	private static final int IMAGE_MAX_SIZE = 200;
	private static final float IMAGE_QUALITY = 0.55f;

	/*
	 * Số ảnh tối đa cho mỗi nhóm:
	 * - Before
	 * - After
	 * - HSE
	 */
	private static final int IMAGE_CAPACITY = 3;

	/*
	 * =========================================================
	 * PATROL REPORT HEADERS
	 * =========================================================
	 *
	 * Thứ tự header PHẢI trùng hoàn toàn với thứ tự ghi dữ liệu
	 * trong writePatrolReportsSheet().
	 */
	private static final String[] REPORT_HEADERS = {
			// ===== GENERAL: 0 - 16 =====
			"Report ID",                    // 0
			"No.",                          // 1
			"QR Key",                       // 2
			"Type",                         // 3
			"Group",                        // 4
			"Plant",                        // 5
			"Division",                     // 6
			"Area",                         // 7
			"Machine",                      // 8

			"Risk Frequency",               // 9
			"Risk Probability",             // 10
			"Risk Severity",                // 11
			"Risk Score",                   // 12

			"Comment",                      // 13
			"Countermeasure",               // 14
			"Check Information",            // 15
			"Patrol User",                  // 16

			// ===== BEFORE: 17 - 20 =====
			"Before Image 1",               // 17
			"Before Image 2",               // 18
			"Before Image 3",               // 19
			"Created At",                   // 20

			// ===== PIC / DUE DATE: 21 - 25 =====
			"PIC",                          // 21
			"Due Date",                     // 22
			"Due Date Update Count",        // 23
			"Due Date Updated By",          // 24
			"Due Date Updated At",          // 25

			// ===== AFTER: 26 - 34 =====
			"After Image 1",                // 26
			"After Image 2",                // 27
			"After Image 3",                // 28
			"After Comment",                // 29
			"After Date",                   // 30
			"After PIC",                    // 31
			"After Status",                 // 32
			"After Assign",                 // 33

			// ===== HSE: 34 - 40 =====
			"HSE Decision",                 // 34
			"HSE Image 1",                  // 35
			"HSE Image 2",                  // 36
			"HSE Image 3",                  // 37
			"HSE Comment",                  // 38
			"HSE Date",                     // 39
			"HSE User",                     // 40

			// ===== SYSTEM: 41 =====
			"Load Status"                   // 41
	};



	/*
	 * Header group indices.
	 */
	private static final int IDX_GENERAL_START = 0;
	private static final int IDX_GENERAL_END = 16;

	private static final int IDX_BEFORE_START = 17;
	private static final int IDX_BEFORE_END = 20;

	private static final int IDX_PIC_DUE_START = 21;
	private static final int IDX_PIC_DUE_END = 25;

	private static final int IDX_AFTER_START = 26;
	private static final int IDX_AFTER_END = 33;

	private static final int IDX_HSE_START = 34;
	private static final int IDX_HSE_END = 40;

	private static final int IDX_SYSTEM_START = 41;
	private static final int IDX_SYSTEM_END = 41;

	/**
	 * Export Excel.
	 */
	public byte[] export(
			String plant,
			String division,
			String area,
			String machine,
			String type,
			String afStatus,
			String grp,
			String pic,
			String patrolUser,
			String qrKey,
			LocalDate from,
			LocalDate to
	) throws Exception {

		List<PatrolReportDTO> reportRows = patrolReportService.search(
				plant,
				division,
				area,
				machine,
				type,
				afStatus,
				grp,
				pic,
				patrolUser,
				qrKey,
				from,
				to
		);

		List<DivisionSummaryDTO> summaryRows =
				patrolReportService.summaryByDivision(
						from,
						to,
						plant,
						type
				);

		SXSSFWorkbook workbook = new SXSSFWorkbook(100);
		workbook.setCompressTempFiles(true);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			Styles styles = new Styles(workbook);

			Sheet reportSheet = workbook.createSheet("Patrol Reports");
			writePatrolReportsSheet(
					workbook,
					reportSheet,
					reportRows,
					styles
			);

			Sheet summarySheet = workbook.createSheet("Division Summary");
			writeDivisionSummarySheet(
					summarySheet,
					summaryRows,
					styles
			);

			workbook.write(outputStream);
			return outputStream.toByteArray();
		} finally {
			try {
				workbook.close();
			} finally {
				workbook.dispose();
			}
		}
	}

	/*
	 * =========================================================
	 * SHEET 1: PATROL REPORTS
	 * =========================================================
	 */

	private void writePatrolReportsSheet(
			SXSSFWorkbook workbook,
			Sheet sheet,
			List<PatrolReportDTO> rows,
			Styles styles
	) {
		if (rows == null) {
			rows = List.of();
		}

		// =========================================================
		// HEADER
		// =========================================================
		Row headerRow = sheet.createRow(0);
		headerRow.setHeightInPoints(32);

		for (int columnIndex = 0;
		     columnIndex < REPORT_HEADERS.length;
		     columnIndex++) {

			Cell cell = headerRow.createCell(columnIndex);
			cell.setCellValue(REPORT_HEADERS[columnIndex]);
			cell.setCellStyle(
					resolveReportHeaderStyle(
							columnIndex,
							styles
					)
			);
		}

		sheet.createFreezePane(0, 1);

		sheet.setAutoFilter(
				new CellRangeAddress(
						0,
						0,
						0,
						REPORT_HEADERS.length - 1
				)
		);

		CreationHelper creationHelper =
				workbook.getCreationHelper();

		Drawing<?> drawing =
				sheet.createDrawingPatriarch();

		// =========================================================
		// DATA
		// =========================================================
		int rowIndex = 1;

		for (PatrolReportDTO dto : rows) {
			Row row = sheet.createRow(rowIndex++);
			row.setHeightInPoints(22f);

			int column = 0;

			// =====================================================
			// GENERAL
			// =====================================================
			setText(
					row,
					column++,
					dto.getId(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getStt(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getQr_key(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getType(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getGrp(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getPlant(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getDivision(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getArea(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getMachine(),
					styles.wrap
			);

			// =====================================================
			// RISK
			// =====================================================
			setText(
					row,
					column++,
					dto.getRiskFreq(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getRiskProb(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getRiskSev(),
					styles.wrap
			);

			setRiskLevelText(
					row,
					column++,
					dto.getRiskTotal(),
					styles.wrap,
					styles.redBold
			);

			// =====================================================
			// PATROL INFORMATION
			// =====================================================
			setText(
					row,
					column++,
					dto.getComment(),
					styles.wrapLeft
			);

			setText(
					row,
					column++,
					dto.getCountermeasure(),
					styles.wrapLeft
			);

			setText(
					row,
					column++,
					dto.getCheckInfo(),
					styles.wrapLeft
			);

			setText(
					row,
					column++,
					dto.getPatrol_user(),
					styles.wrap
			);

			// =====================================================
			// BEFORE IMAGES
			// =====================================================
			for (int imageIndex = 0;
			     imageIndex < IMAGE_CAPACITY;
			     imageIndex++) {

				addImageCell(
						workbook,
						sheet,
						drawing,
						creationHelper,
						row,
						column++,
						imageAt(
								dto.getImageNames(),
								imageIndex
						),
						styles.imageCell
				);
			}

			setText(
					row,
					column++,
					formatDateTime(dto.getCreatedAt()),
					styles.wrap
			);

			// =====================================================
			// PIC / DUE DATE
			// =====================================================
			setText(
					row,
					column++,
					dto.getPic(),
					styles.wrap
			);

			setText(
					row,
					column++,
					formatDateTime(dto.getDueDate()),
					styles.wrap
			);

			setNumberOrBlank(
					row,
					column++,
					dto.getDueDateUpdateCount(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getDueDateUpdatedBy(),
					styles.wrap
			);

			setText(
					row,
					column++,
					formatDateTime(dto.getDueDateUpdatedAt()),
					styles.wrap
			);

			// =====================================================
			// AFTER IMAGES
			// =====================================================
			for (int imageIndex = 0;
			     imageIndex < IMAGE_CAPACITY;
			     imageIndex++) {

				addImageCell(
						workbook,
						sheet,
						drawing,
						creationHelper,
						row,
						column++,
						imageAt(
								dto.getAt_imageNames(),
								imageIndex
						),
						styles.imageCell
				);
			}

			// =====================================================
			// AFTER INFORMATION
			// =====================================================
			setText(
					row,
					column++,
					dto.getAt_comment(),
					styles.wrapLeft
			);

			setText(
					row,
					column++,
					formatDateTime(dto.getAt_date()),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getAt_pic(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getAt_status(),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getAt_assign(),
					styles.wrap
			);

			// =====================================================
			// HSE
			// =====================================================
			setText(
					row,
					column++,
					dto.getHse_judge(),
					styles.wrap
			);

			for (int imageIndex = 0;
			     imageIndex < IMAGE_CAPACITY;
			     imageIndex++) {

				addImageCell(
						workbook,
						sheet,
						drawing,
						creationHelper,
						row,
						column++,
						imageAt(
								dto.getHse_imageNames(),
								imageIndex
						),
						styles.imageCell
				);
			}

			setText(
					row,
					column++,
					dto.getHse_comment(),
					styles.wrapLeft
			);

			setText(
					row,
					column++,
					formatDateTime(dto.getHse_date()),
					styles.wrap
			);

			setText(
					row,
					column++,
					dto.getHse_user(),
					styles.wrap
			);

			// =====================================================
			// SYSTEM
			// =====================================================
			setText(
					row,
					column++,
					dto.getLoad_status(),
					styles.wrap
			);

			// =====================================================
			// VALIDATE COLUMN COUNT
			// =====================================================
			if (column != REPORT_HEADERS.length) {
				throw new IllegalStateException(
						"Patrol export column mismatch. "
								+ "Written columns="
								+ column
								+ ", header columns="
								+ REPORT_HEADERS.length
				);
			}
		}

		applyReportColumnWidths(sheet);
	}



	/**
	 * Chọn màu header theo nhóm.
	 */
	private CellStyle resolveReportHeaderStyle(
			int index,
			Styles styles
	) {
		if (index >= IDX_BEFORE_START
				&& index <= IDX_BEFORE_END) {
			return styles.headerBlue;
		}

		if (index >= IDX_PIC_DUE_START
				&& index <= IDX_PIC_DUE_END) {
			return styles.headerGreen;
		}

		if (index >= IDX_AFTER_START
				&& index <= IDX_AFTER_END) {
			return styles.headerGreen;
		}

		if (index >= IDX_HSE_START
				&& index <= IDX_HSE_END) {
			return styles.headerYellow;
		}

		if (index >= IDX_SYSTEM_START
				&& index <= IDX_SYSTEM_END) {
			return styles.headerGrey;
		}

		return styles.headerDefault;
	}

	/**
	 * Width cho sheet Patrol Reports.
	 */
	private void applyReportColumnWidths(Sheet sheet) {
		// Default
		for (int i = 0; i < REPORT_HEADERS.length; i++) {
			setColumnWidth(sheet, i, 16);
		}

		// ID / QR
		setColumnWidth(sheet, 0, 9);   // Report ID
		setColumnWidth(sheet, 1, 7);   // No.
		setColumnWidth(sheet, 2, 11);  // QR Key

		// Basic info
		setColumnWidth(sheet, 3, 12);  // Type
		setColumnWidth(sheet, 4, 14);  // Group
		setColumnWidth(sheet, 5, 12);  // Plant
		setColumnWidth(sheet, 6, 16);  // Division
		setColumnWidth(sheet, 7, 18);  // Area
		setColumnWidth(sheet, 8, 16);  // Machine

		// Risk
		setColumnWidth(sheet, 9, 12);
		setColumnWidth(sheet, 10, 12);
		setColumnWidth(sheet, 11, 12);
		setColumnWidth(sheet, 12, 9);

		// Text lớn
		setColumnWidth(sheet, 13, 55); // Comment
		setColumnWidth(sheet, 14, 45); // Countermeasure
		setColumnWidth(sheet, 15, 35); // Check Information
		setColumnWidth(sheet, 16, 16); // Patrol User

		// Before images
		setColumnWidth(sheet, 17, 16);
		setColumnWidth(sheet, 18, 16);
		setColumnWidth(sheet, 19, 16);
		setColumnWidth(sheet, 20, 18); // Created At

		// PIC / Due date
		setColumnWidth(sheet, 21, 16); // PIC
		setColumnWidth(sheet, 22, 14); // Due Date
		setColumnWidth(sheet, 23, 12); // Update Count
		setColumnWidth(sheet, 24, 16); // Updated By
		setColumnWidth(sheet, 25, 18); // Updated At

		// After images
		setColumnWidth(sheet, 26, 16);
		setColumnWidth(sheet, 27, 16);
		setColumnWidth(sheet, 28, 16);

		// After info
		setColumnWidth(sheet, 29, 50); // After Comment
		setColumnWidth(sheet, 30, 18); // After Date
		setColumnWidth(sheet, 31, 16); // After PIC
		setColumnWidth(sheet, 32, 10); // After Status nhỏ lại
		setColumnWidth(sheet, 33, 16); // After Assign

		// HSE
		setColumnWidth(sheet, 34, 11); // HSE Decision / Status nhỏ lại
		setColumnWidth(sheet, 35, 16);
		setColumnWidth(sheet, 36, 16);
		setColumnWidth(sheet, 37, 16);
		setColumnWidth(sheet, 38, 50); // HSE Comment
		setColumnWidth(sheet, 39, 18); // HSE Date
		setColumnWidth(sheet, 40, 16); // HSE User

		// System
		setColumnWidth(sheet, 41, 10); // Load Status nhỏ lại
	}

	private void setColumnWidth(
			Sheet sheet,
			int columnIndex,
			int characterWidth
	) {
		int excelWidth = characterWidth * 256;
		int maxWidth = 255 * 256;

		sheet.setColumnWidth(
				columnIndex,
				Math.min(excelWidth, maxWidth)
		);
	}

	/*
	 * =========================================================
	 * SHEET 2: DIVISION SUMMARY
	 * =========================================================
	 */
	private void writeDivisionSummarySheet(
			Sheet sheet,
			List<DivisionSummaryDTO> rows,
			Styles styles
	) {
		if (rows == null) {
			rows = List.of();
		}

		String[] headers = {
				"Division",

				"All_TTL",
				"All_I",
				"All_II",
				"All_III",
				"All_IV",
				"All_V",

				"Pro_Done_TTL",
				"Pro_Done_I",
				"Pro_Done_II",
				"Pro_Done_III",
				"Pro_Done_IV",
				"Pro_Done_V",

				"Remain_TTL",
				"Remain_I",
				"Remain_II",
				"Remain_III",
				"Remain_IV",
				"Remain_V",

				"Still_Time",
				"Within_3_Days",
				"Late",

				"HSE_Done_TTL",
				"HSE_Done_I",
				"HSE_Done_II",
				"HSE_Done_III",
				"HSE_Done_IV",
				"HSE_Done_V"
		};

		/*
		 * Header.
		 */
		Row headerRow = sheet.createRow(0);
		headerRow.setHeightInPoints(30);

		for (int i = 0; i < headers.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(headers[i]);

			if (i >= 1 && i <= 6) {
				cell.setCellStyle(styles.headerBlue);
			} else if (i >= 7 && i <= 12) {
				cell.setCellStyle(styles.headerGreen);
			} else if (i >= 13 && i <= 18) {
				cell.setCellStyle(styles.headerRed);
			} else if (i >= 19 && i <= 21) {
				cell.setCellStyle(styles.headerOrange);
			} else if (i >= 22 && i <= 27) {
				cell.setCellStyle(styles.headerYellow);
			} else {
				cell.setCellStyle(styles.headerDefault);
			}
		}

		sheet.createFreezePane(1, 1);

		sheet.setAutoFilter(
				new CellRangeAddress(
						0,
						0,
						0,
						headers.length - 1
				)
		);

		/*
		 * Data.
		 *
		 * PatrolReportService.summaryByDivision() hiện đã thêm:
		 * - SUM
		 * - %
		 *
		 * nên xử lý style riêng cho hai dòng này.
		 */
		int rowIndex = 1;

		for (DivisionSummaryDTO dto : rows) {
			Row row = sheet.createRow(rowIndex++);

			boolean sumRow =
					"SUM".equalsIgnoreCase(dto.getDivision());

			boolean percentRow =
					"%".equalsIgnoreCase(dto.getDivision());

			int column = 0;

			CellStyle labelStyle = sumRow
					? styles.sumLabel
					: percentRow
					? styles.percentLabel
					: styles.left;

			CellStyle numberStyle = sumRow
					? styles.sumCell
					: styles.cell;

			setText(
					row,
					column++,
					dto.getDivision(),
					labelStyle
			);

			if (percentRow) {
				/*
				 * ALL.
				 */
				setPercentValue(
						row,
						column++,
						dto.getAllTtl(),
						styles.percentCell
				);

				for (int i = 0; i < 5; i++) {
					setBlank(
							row,
							column++,
							styles.percentCell
					);
				}

				/*
				 * PRO DONE.
				 */
				setPercentValue(
						row,
						column++,
						dto.getProDoneTtl(),
						styles.percentCell
				);

				for (int i = 0; i < 5; i++) {
					setBlank(
							row,
							column++,
							styles.percentCell
					);
				}

				/*
				 * REMAIN.
				 */
				setPercentValue(
						row,
						column++,
						dto.getRemainTtl(),
						styles.percentRemain
				);

				for (int i = 0; i < 5; i++) {
					setBlank(
							row,
							column++,
							styles.percentRemain
					);
				}

				/*
				 * Deadline.
				 */
				for (int i = 0; i < 3; i++) {
					setBlank(
							row,
							column++,
							styles.percentCell
					);
				}

				/*
				 * HSE DONE.
				 */
				setPercentValue(
						row,
						column++,
						dto.getHseDoneTtl(),
						styles.percentCell
				);

				for (int i = 0; i < 5; i++) {
					setBlank(
							row,
							column++,
							styles.percentCell
					);
				}

				continue;
			}

			/*
			 * ALL.
			 */
			setNumber(row, column++, dto.getAllTtl(), numberStyle);
			setNumber(row, column++, dto.getAllI(), numberStyle);
			setNumber(row, column++, dto.getAllII(), numberStyle);
			setNumber(row, column++, dto.getAllIII(), numberStyle);
			setNumber(row, column++, dto.getAllIV(), numberStyle);
			setNumber(row, column++, dto.getAllV(), numberStyle);

			/*
			 * PRO DONE.
			 */
			setNumber(row, column++, dto.getProDoneTtl(), numberStyle);
			setNumber(row, column++, dto.getProDoneI(), numberStyle);
			setNumber(row, column++, dto.getProDoneII(), numberStyle);
			setNumber(row, column++, dto.getProDoneIII(), numberStyle);
			setNumber(row, column++, dto.getProDoneIV(), numberStyle);
			setNumber(row, column++, dto.getProDoneV(), numberStyle);

			/*
			 * REMAIN.
			 */
			CellStyle remainStyle = sumRow
					? styles.sumRemain
					: styles.cell;

			setNumber(row, column++, dto.getRemainTtl(), remainStyle);
			setNumber(row, column++, dto.getRemainI(), remainStyle);
			setNumber(row, column++, dto.getRemainII(), remainStyle);
			setNumber(row, column++, dto.getRemainIII(), remainStyle);
			setNumber(row, column++, dto.getRemainIV(), remainStyle);
			setNumber(row, column++, dto.getRemainV(), remainStyle);

			/*
			 * DEADLINE.
			 */
			setNumber(row, column++, dto.getStillTime(), numberStyle);
			setNumber(row, column++, dto.getThreeDaysAgo(), numberStyle);
			setNumber(row, column++, dto.getLate(), remainStyle);

			/*
			 * HSE DONE.
			 */
			setNumber(row, column++, dto.getHseDoneTtl(), numberStyle);
			setNumber(row, column++, dto.getHseDoneI(), numberStyle);
			setNumber(row, column++, dto.getHseDoneII(), numberStyle);
			setNumber(row, column++, dto.getHseDoneIII(), numberStyle);
			setNumber(row, column++, dto.getHseDoneIV(), numberStyle);
			setNumber(row, column++, dto.getHseDoneV(), numberStyle);
		}

		/*
		 * Width.
		 */
		setColumnWidth(sheet, 0, 20);

		for (int i = 1; i < headers.length; i++) {
			setColumnWidth(sheet, i, 14);
		}

		setColumnWidth(sheet, 19, 16);
		setColumnWidth(sheet, 20, 16);
		setColumnWidth(sheet, 21, 14);
	}

	/*
	 * =========================================================
	 * CELL HELPERS
	 * =========================================================
	 */
	private void setText(
			Row row,
			int column,
			Object value,
			CellStyle style
	) {
		Cell cell = row.createCell(column);
		cell.setCellValue(
				value == null
						? ""
						: String.valueOf(value)
		);

		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void setNumber(
			Row row,
			int column,
			Number value,
			CellStyle style
	) {
		Cell cell = row.createCell(column);

		if (value == null) {
			cell.setBlank();
		} else {
			cell.setCellValue(value.doubleValue());
		}

		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void setNumberOrBlank(
			Row row,
			int column,
			Number value,
			CellStyle style
	) {
		setNumber(
				row,
				column,
				value,
				style
		);
	}

	/**
	 * DTO percent row đang lưu dạng 72.22.
	 * Excel percent phải nhận 0.7222.
	 */
	private void setPercentValue(
			Row row,
			int column,
			Number percent,
			CellStyle style
	) {
		Cell cell = row.createCell(column);

		if (percent == null) {
			cell.setBlank();
		} else {
			cell.setCellValue(
					percent.doubleValue() / 100.0
			);
		}

		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void setBlank(
			Row row,
			int column,
			CellStyle style
	) {
		Cell cell = row.createCell(column);
		cell.setBlank();

		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private void setRiskLevelText(
			Row row,
			int column,
			Object value,
			CellStyle normalStyle,
			CellStyle dangerStyle
	) {
		Cell cell = row.createCell(column);

		String riskLevel =
				value == null
						? ""
						: String.valueOf(value).trim();

		cell.setCellValue(riskLevel);

		if ("IV".equalsIgnoreCase(riskLevel)
				|| "V".equalsIgnoreCase(riskLevel)) {
			cell.setCellStyle(dangerStyle);
		} else {
			cell.setCellStyle(normalStyle);
		}
	}

	/*
	 * =========================================================
	 * DATE HELPERS
	 * =========================================================
	 */
	private String formatDate(LocalDate value) {
		return value == null
				? ""
				: DATE_FORMATTER.format(value);
	}

	private String formatDateTime(LocalDateTime value) {
		return value == null
				? ""
				: DATE_TIME_FORMATTER.format(value);
	}

	/*
	 * =========================================================
	 * IMAGE HELPERS
	 * =========================================================
	 */
	private List<String> cleanImages(List<String> images) {
		if (images == null) {
			return List.of();
		}

		return images.stream()
				.filter(image ->
						image != null
								&& !image.isBlank()
				)
				.map(String::trim)
				.toList();
	}

	private String imageAt(
			List<String> images,
			int index
	) {
		List<String> cleanedImages =
				cleanImages(images);

		if (index < 0
				|| index >= cleanedImages.size()) {
			return "";
		}

		return cleanedImages.get(index);
	}

	private byte[] compressToJpegThumbnail(
			byte[] sourceBytes
	) throws Exception {
		try (
				ByteArrayInputStream inputStream =
						new ByteArrayInputStream(sourceBytes);

				ByteArrayOutputStream outputStream =
						new ByteArrayOutputStream()
		) {
			Thumbnails.of(inputStream)
					.size(
							IMAGE_MAX_SIZE,
							IMAGE_MAX_SIZE
					)
					.outputFormat("jpg")
					.outputQuality(IMAGE_QUALITY)
					.toOutputStream(outputStream);

			return outputStream.toByteArray();
		}
	}

	private void addImageCell(
			Workbook workbook,
			Sheet sheet,
			Drawing<?> drawing,
			CreationHelper creationHelper,
			Row row,
			int column,
			String imageName,
			CellStyle cellStyle
	) {
		Cell cell = row.createCell(column);

		if (cellStyle != null) {
			cell.setCellStyle(cellStyle);
		}

		if (imageName == null
				|| imageName.isBlank()) {
			return;
		}

		try {
			String imageUrl =
					IMAGE_BASE_URL + imageName.trim();

			byte[] originalBytes;

			try (InputStream inputStream =
					     new URL(imageUrl).openStream()) {

				originalBytes =
						IOUtils.toByteArray(inputStream);
			}

			byte[] thumbnailBytes =
					compressToJpegThumbnail(originalBytes);

			int pictureIndex =
					workbook.addPicture(
							thumbnailBytes,
							Workbook.PICTURE_TYPE_JPEG
					);

			ClientAnchor anchor =
					creationHelper.createClientAnchor();

			anchor.setCol1(column);
			anchor.setRow1(row.getRowNum());
			anchor.setCol2(column + 1);
			anchor.setRow2(row.getRowNum() + 1);

			int padding = 2 * 9525;

			anchor.setDx1(padding);
			anchor.setDy1(padding);
			anchor.setDx2(-padding);
			anchor.setDy2(-padding);

			anchor.setAnchorType(
					ClientAnchor.AnchorType.MOVE_AND_RESIZE
			);

			Picture picture =
					drawing.createPicture(
							anchor,
							pictureIndex
					);

			/*
			 * Không gọi picture.resize() vì có thể làm lệch layout.
			 */
			if (picture != null) {
				row.setHeightInPoints(
						Math.max(
								row.getHeightInPoints(),
								82f
						)
				);
			}

			int minimumWidth = 16 * 256;

			if (sheet.getColumnWidth(column)
					< minimumWidth) {
				sheet.setColumnWidth(
						column,
						minimumWidth
				);
			}
		} catch (Exception exception) {
			/*
			 * Không làm fail toàn bộ file Excel nếu một ảnh lỗi.
			 * Ghi tên file vào cell để người dùng vẫn biết ảnh nào lỗi.
			 */
			cell.setCellValue(imageName);
		}
	}

	/*
	 * =========================================================
	 * STYLES
	 * =========================================================
	 */
	private static class Styles {

		final CellStyle headerBlue;
		final CellStyle headerGreen;
		final CellStyle headerYellow;
		final CellStyle headerRed;
		final CellStyle headerOrange;
		final CellStyle headerGrey;
		final CellStyle headerDefault;

		final CellStyle wrap;
		final CellStyle wrapLeft;
		final CellStyle redBold;
		final CellStyle imageCell;

		final CellStyle cell;
		final CellStyle left;

		final CellStyle sumLabel;
		final CellStyle sumCell;
		final CellStyle sumRemain;

		final CellStyle percentLabel;
		final CellStyle percentCell;
		final CellStyle percentRemain;

		Styles(SXSSFWorkbook workbook) {
			Font headerFont =
					workbook.createFont();

			headerFont.setBold(true);

			headerBlue = createHeader(
					workbook,
					headerFont,
					IndexedColors.LIGHT_CORNFLOWER_BLUE
			);

			headerGreen = createHeader(
					workbook,
					headerFont,
					IndexedColors.LIGHT_GREEN
			);

			headerYellow = createHeader(
					workbook,
					headerFont,
					IndexedColors.LIGHT_YELLOW
			);

			headerRed = createHeader(
					workbook,
					headerFont,
					IndexedColors.ROSE
			);

			headerOrange = createHeader(
					workbook,
					headerFont,
					IndexedColors.LIGHT_ORANGE
			);

			headerGrey = createHeader(
					workbook,
					headerFont,
					IndexedColors.GREY_25_PERCENT
			);

			headerDefault =
					workbook.createCellStyle();

			headerDefault.setFont(headerFont);
			headerDefault.setAlignment(
					HorizontalAlignment.CENTER
			);
			headerDefault.setVerticalAlignment(
					VerticalAlignment.CENTER
			);
			headerDefault.setWrapText(true);
			setBorder(headerDefault);

			/*
			 * Normal centered wrap.
			 */
			wrap = workbook.createCellStyle();
			wrap.setWrapText(true);
			wrap.setAlignment(
					HorizontalAlignment.CENTER
			);
			wrap.setVerticalAlignment(
					VerticalAlignment.CENTER
			);
			setBorder(wrap);

			/*
			 * Long text, align left.
			 */
			wrapLeft =
					workbook.createCellStyle();

			wrapLeft.cloneStyleFrom(wrap);
			wrapLeft.setAlignment(
					HorizontalAlignment.LEFT
			);

			/*
			 * Risk IV/V.
			 */
			redBold =
					workbook.createCellStyle();

			redBold.cloneStyleFrom(wrap);

			Font dangerFont =
					workbook.createFont();

			dangerFont.setBold(true);
			dangerFont.setColor(
					IndexedColors.RED.getIndex()
			);

			redBold.setFont(dangerFont);

			/*
			 * Empty image cell.
			 */
			imageCell =
					workbook.createCellStyle();

			imageCell.setAlignment(
					HorizontalAlignment.CENTER
			);
			imageCell.setVerticalAlignment(
					VerticalAlignment.CENTER
			);
			setBorder(imageCell);

			/*
			 * Summary normal cells.
			 */
			cell = workbook.createCellStyle();
			cell.setAlignment(
					HorizontalAlignment.CENTER
			);
			cell.setVerticalAlignment(
					VerticalAlignment.CENTER
			);
			cell.setWrapText(true);
			setBorder(cell);

			left = workbook.createCellStyle();
			left.cloneStyleFrom(cell);
			left.setAlignment(
					HorizontalAlignment.LEFT
			);

			/*
			 * SUM row.
			 */
			sumLabel =
					workbook.createCellStyle();

			sumLabel.cloneStyleFrom(left);
			sumLabel.setFillForegroundColor(
					IndexedColors.GREY_25_PERCENT.getIndex()
			);
			sumLabel.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);

			Font sumFont =
					workbook.createFont();

			sumFont.setBold(true);
			sumLabel.setFont(sumFont);

			sumCell =
					workbook.createCellStyle();

			sumCell.cloneStyleFrom(cell);
			sumCell.setFillForegroundColor(
					IndexedColors.GREY_25_PERCENT.getIndex()
			);
			sumCell.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);
			sumCell.setFont(sumFont);

			sumRemain =
					workbook.createCellStyle();

			sumRemain.cloneStyleFrom(cell);
			sumRemain.setFillForegroundColor(
					IndexedColors.ROSE.getIndex()
			);
			sumRemain.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);
			sumRemain.setFont(sumFont);

			/*
			 * Percent row.
			 */
			percentLabel =
					workbook.createCellStyle();

			percentLabel.cloneStyleFrom(left);
			percentLabel.setFillForegroundColor(
					IndexedColors.GREY_25_PERCENT.getIndex()
			);
			percentLabel.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);
			percentLabel.setFont(sumFont);

			percentCell =
					workbook.createCellStyle();

			percentCell.cloneStyleFrom(cell);
			percentCell.setFillForegroundColor(
					IndexedColors.GREY_25_PERCENT.getIndex()
			);
			percentCell.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);
			percentCell.setDataFormat(
					workbook
							.createDataFormat()
							.getFormat("0.00%")
			);
			percentCell.setFont(sumFont);

			percentRemain =
					workbook.createCellStyle();

			percentRemain.cloneStyleFrom(percentCell);
			percentRemain.setFillForegroundColor(
					IndexedColors.ROSE.getIndex()
			);
			percentRemain.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);
		}

		private static CellStyle createHeader(
				SXSSFWorkbook workbook,
				Font headerFont,
				IndexedColors color
		) {
			CellStyle style =
					workbook.createCellStyle();

			style.setFont(headerFont);
			style.setAlignment(
					HorizontalAlignment.CENTER
			);
			style.setVerticalAlignment(
					VerticalAlignment.CENTER
			);
			style.setWrapText(true);
			style.setFillForegroundColor(
					color.getIndex()
			);
			style.setFillPattern(
					FillPatternType.SOLID_FOREGROUND
			);

			setBorder(style);

			return style;
		}

		private static void setBorder(
				CellStyle style
		) {
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
		}
	}
}