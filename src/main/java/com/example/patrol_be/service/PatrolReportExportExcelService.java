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

    private static final String IMAGE_BASE_URL = "http://192.168.122.16:8002/images/";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Thumb
    private static final int IMG_MAX = 200;
    private static final float IMG_QUALITY = 0.55f;

    // fixed 3 images each group
    private static final int CAP = 3;

    // Patrol Reports headers
    private static final String[] REPORT_HEADERS = {
            "Report ID", "No.", "QR Key", "Type", "Group", "Plant", "Division", "Area", "Machine",
            "Risk Frequency", "Risk Probability", "Risk Severity", "Risk Score",
            "Comment", "Countermeasure", "Check Information", "Patrol User",
            "Before Image 1", "Before Image 2", "Before Image 3", "Created At", "PIC", "Due Date",
            "After Image 1", "After Image 2", "After Image 3",
            "After Comment", "After Date", "After PIC", "After Status",
            "HSE Decision", "HSE Image 1", "HSE Image 2", "HSE Image 3", "HSE Comment", "HSE Date",
            "Load Status"
    };

    // Indices for header color groups (by your current layout)
    private static final int IDX_PATROL_USER = 16;
    private static final int IDX_BEFORE_IMG_START = 17;
    private static final int IDX_BEFORE_IMG_END = 19;     // inclusive
    private static final int IDX_CREATED_AT = 20;

    private static final int IDX_PIC = 21;
    private static final int IDX_DUE_DATE = 22;
    private static final int IDX_AFTER_IMG_START = 23;
    private static final int IDX_AFTER_IMG_END = 25;      // inclusive
    private static final int IDX_AFTER_GROUP_END = 29;    // includes after comment/date/pic/status

    private static final int IDX_HSE_START = 30;
    private static final int IDX_HSE_END = 35;            // inclusive

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

        List<PatrolReportDTO> rows = patrolReportService.search(
                plant, division, area, machine, type, afStatus, grp, pic, patrolUser, qrKey, from, to
        );

        List<DivisionSummaryDTO> sumRows =
                patrolReportService.summaryByDivision(from, to, plant, type);

        try (SXSSFWorkbook wb = new SXSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            wb.setCompressTempFiles(true);

            Styles styles = new Styles(wb);

            Sheet sheet1 = wb.createSheet("Patrol Reports");
            writePatrolReportsSheet(wb, sheet1, rows, styles);

            Sheet sheet2 = wb.createSheet("Division Summary");
            writeDivisionSummarySheet(wb, sheet2, sumRows, styles);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // =========================
    // Sheet 1: Patrol Reports
    // =========================
    private void writePatrolReportsSheet(
            SXSSFWorkbook wb,
            Sheet sheet,
            List<PatrolReportDTO> rows,
            Styles styles
    ) {
        if (rows == null) rows = List.of();

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(28);

        for (int i = 0; i < REPORT_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(REPORT_HEADERS[i]);
            cell.setCellStyle(resolveReportHeaderStyle(i, styles));
        }

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, REPORT_HEADERS.length - 1));

        CreationHelper helper = wb.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        int rIndex = 1;
        for (PatrolReportDTO d : rows) {
            Row r = sheet.createRow(rIndex++);
            r.setHeight((short) -1); // auto height on open

            int col = 0;

            setText(r, col++, d.getId(), styles.wrap);
            setText(r, col++, d.getStt(), styles.wrap);
            setText(r, col++, d.getQr_key(), styles.wrap);
            setText(r, col++, d.getType(), styles.wrap);
            setText(r, col++, d.getGrp(), styles.wrap);
            setText(r, col++, d.getPlant(), styles.wrap);
            setText(r, col++, d.getDivision(), styles.wrap);
            setText(r, col++, d.getArea(), styles.wrap);
            setText(r, col++, d.getMachine(), styles.wrap);

            setText(r, col++, d.getRiskFreq(), styles.wrap);
            setText(r, col++, d.getRiskProb(), styles.wrap);
            setText(r, col++, d.getRiskSev(), styles.wrap);
            setRiskLevelText(r, col++, d.getRiskTotal(), styles.wrap, styles.redBold);

            setText(r, col++, d.getComment(), styles.wrap);
            setText(r, col++, d.getCountermeasure(), styles.wrap);
            setText(r, col++, d.getCheckInfo(), styles.wrap);
            setText(r, col++, d.getPatrol_user(), styles.wrap);

            // BEFORE IMG (3)
            for (int i = 0; i < CAP; i++) {
                addImageCell(wb, sheet, drawing, helper, r, col++, imgAt(d.getImageNames(), i));
            }

            setText(r, col++, fmt(d.getCreatedAt()), styles.wrap);
            setText(r, col++, d.getPic(), styles.wrap);
            setText(r, col++, fmt(d.getDueDate()), styles.wrap);

            // AFTER IMG (3)
            for (int i = 0; i < CAP; i++) {
                addImageCell(wb, sheet, drawing, helper, r, col++, imgAt(d.getAt_imageNames(), i));
            }

            setText(r, col++, d.getAt_comment(), styles.wrap);
            setText(r, col++, fmt(d.getAt_date()), styles.wrap);
            setText(r, col++, d.getAt_pic(), styles.wrap);
            setText(r, col++, d.getAt_status(), styles.wrap);

            setText(r, col++, d.getHse_judge(), styles.wrap);

            // HSE image names (text)
            setText(r, col++, imgAt(d.getHse_imageNames(), 0), styles.wrap);
            setText(r, col++, imgAt(d.getHse_imageNames(), 1), styles.wrap);
            setText(r, col++, imgAt(d.getHse_imageNames(), 2), styles.wrap);

            setText(r, col++, d.getHse_comment(), styles.wrap);
            setText(r, col++, fmt(d.getHse_date()), styles.wrap);

            setText(r, col++, d.getLoad_status(), styles.wrap);
        }

        applyReportColumnWidths(sheet);
    }

    private CellStyle resolveReportHeaderStyle(int idx, Styles styles) {
        // 🟦 Patrol / Before
        if (idx == IDX_PATROL_USER || (idx >= IDX_BEFORE_IMG_START && idx <= IDX_BEFORE_IMG_END) || idx == IDX_CREATED_AT) {
            return styles.headerBlue;
        }
        // 🟩 PIC / After
        if (idx == IDX_PIC || idx == IDX_DUE_DATE || (idx >= IDX_AFTER_IMG_START && idx <= IDX_AFTER_GROUP_END)) {
            return styles.headerGreen;
        }
        // 🟨 HSE
        if (idx >= IDX_HSE_START && idx <= IDX_HSE_END) {
            return styles.headerYellow;
        }
        return styles.headerDefault;
    }

    private void applyReportColumnWidths(Sheet sheet) {
        // default
        for (int i = 0; i < REPORT_HEADERS.length; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }

        // narrower IDs
        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 10 * 256);

        // big text columns
        sheet.setColumnWidth(13, 40 * 256); // comment
        sheet.setColumnWidth(14, 40 * 256); // countermeasure
        sheet.setColumnWidth(15, 40 * 256); // checkInfo
        sheet.setColumnWidth(26, 40 * 256); // at_comment
        sheet.setColumnWidth(34, 40 * 256); // hse_comment

        // HSE image name columns
        sheet.setColumnWidth(31, 22 * 256);
        sheet.setColumnWidth(32, 22 * 256);
        sheet.setColumnWidth(33, 22 * 256);
    }

    // =========================
    // Sheet 2: Division Summary
    // (REMAIN before HSE)
    // =========================
    private void writeDivisionSummarySheet(
            SXSSFWorkbook wb,
            Sheet sheet,
            List<DivisionSummaryDTO> rows,
            Styles styles
    ) {
        if (rows == null) rows = List.of();

        String[] headers = {
                "Division",
                "All_TTL", "All_I", "All_II", "All_III", "All_IV", "All_V",
                "Pro_Done_TTL", "Pro_Done_I", "Pro_Done_II", "Pro_Done_III", "Pro_Done_IV", "Pro_Done_V",
                "Remain_TTL", "Remain_I", "Remain_II", "Remain_III", "Remain_IV", "Remain_V",
                "HSE_Done_TTL", "HSE_Done_I", "HSE_Done_II", "HSE_Done_III", "HSE_Done_IV", "HSE_Done_V"
        };

        // Header
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(28);

        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);

            if (i >= 1 && i <= 6) c.setCellStyle(styles.headerBlue);          // ALL
            else if (i >= 7 && i <= 12) c.setCellStyle(styles.headerGreen);   // PRO
            else if (i >= 13 && i <= 18) c.setCellStyle(styles.headerRed);    // REMAIN
            else if (i >= 19 && i <= 24) c.setCellStyle(styles.headerYellow); // HSE
            else c.setCellStyle(styles.headerDefault);
        }

        sheet.createFreezePane(0, 1);

        // Data
        int r = 1;
        for (DivisionSummaryDTO d : rows) {
            Row row = sheet.createRow(r++);
            int c = 0;

            setText(row, c++, d.getDivision(), styles.left);

            // ALL
            setNumber(row, c++, toDoubleSafe(d.getAllTtl()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getAllI()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getAllII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getAllIII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getAllIV()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getAllV()), styles.cell);

            // PRO
            setNumber(row, c++, toDoubleSafe(d.getProDoneTtl()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getProDoneI()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getProDoneII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getProDoneIII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getProDoneIV()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getProDoneV()), styles.cell);

            // REMAIN
            setNumber(row, c++, toDoubleSafe(d.getRemainTtl()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getRemainI()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getRemainII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getRemainIII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getRemainIV()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getRemainV()), styles.cell);

            // HSE
            setNumber(row, c++, toDoubleSafe(d.getHseDoneTtl()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getHseDoneI()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getHseDoneII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getHseDoneIII()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getHseDoneIV()), styles.cell);
            setNumber(row, c++, toDoubleSafe(d.getHseDoneV()), styles.cell);
        }

        // SUM
        Totals t = new Totals();
        for (DivisionSummaryDTO d : rows) t.add(d);

        Row sumRow = sheet.createRow(r++);
        int c = 0;
        setText(sumRow, c++, "SUM", styles.sumLabel);

        // ALL
        setNumber(sumRow, c++, t.allTtl, styles.sumCell);
        setNumber(sumRow, c++, t.allI, styles.sumCell);
        setNumber(sumRow, c++, t.allII, styles.sumCell);
        setNumber(sumRow, c++, t.allIII, styles.sumCell);
        setNumber(sumRow, c++, t.allIV, styles.sumCell);
        setNumber(sumRow, c++, t.allV, styles.sumCell);

        // PRO
        setNumber(sumRow, c++, t.proTtl, styles.sumCell);
        setNumber(sumRow, c++, t.proI, styles.sumCell);
        setNumber(sumRow, c++, t.proII, styles.sumCell);
        setNumber(sumRow, c++, t.proIII, styles.sumCell);
        setNumber(sumRow, c++, t.proIV, styles.sumCell);
        setNumber(sumRow, c++, t.proV, styles.sumCell);

        // REMAIN (đỏ)
        setNumber(sumRow, c++, t.remTtl, styles.sumRemain);
        setNumber(sumRow, c++, t.remI, styles.sumRemain);
        setNumber(sumRow, c++, t.remII, styles.sumRemain);
        setNumber(sumRow, c++, t.remIII, styles.sumRemain);
        setNumber(sumRow, c++, t.remIV, styles.sumRemain);
        setNumber(sumRow, c++, t.remV, styles.sumRemain);

        // HSE
        setNumber(sumRow, c++, t.hseTtl, styles.sumCell);
        setNumber(sumRow, c++, t.hseI, styles.sumCell);
        setNumber(sumRow, c++, t.hseII, styles.sumCell);
        setNumber(sumRow, c++, t.hseIII, styles.sumCell);
        setNumber(sumRow, c++, t.hseIV, styles.sumCell);
        setNumber(sumRow, c++, t.hseV, styles.sumCell);

        // % row (only TTL columns)
        Row pctRow = sheet.createRow(r++);
        int pc = 0;
        setText(pctRow, pc++, "%", styles.pctLabel);

        // All_TTL=100%
        setPercent(pctRow, pc++, 1.0, styles.pct);

        // All_I..All_V blank
        for (int i = 0; i < 5; i++) setBlank(pctRow, pc++, styles.pctLabel);

        // Pro_TTL
        setPercent(pctRow, pc++, t.allTtl == 0 ? 0 : (t.proTtl / t.allTtl), styles.pct);
        for (int i = 0; i < 5; i++) setBlank(pctRow, pc++, styles.pctLabel);

        // Rem_TTL
        setPercent(pctRow, pc++, t.allTtl == 0 ? 0 : (t.remTtl / t.allTtl), styles.pct);
        for (int i = 0; i < 5; i++) setBlank(pctRow, pc++, styles.pctLabel);

        // Hse_TTL
        setPercent(pctRow, pc++, t.allTtl == 0 ? 0 : (t.hseTtl / t.allTtl), styles.pct);
        for (int i = 0; i < 5; i++) setBlank(pctRow, pc++, styles.pctLabel);

        // widths
        sheet.setColumnWidth(0, 18 * 300);
        for (int i = 1; i < headers.length; i++) sheet.setColumnWidth(i, 12 * 300);
    }

    // =========================
    // Styles container
    // =========================
    private static class Styles {
        final CellStyle headerBlue, headerGreen, headerYellow, headerRed, headerDefault;
        final CellStyle wrap;
        final CellStyle redBold;

        final CellStyle cell;
        final CellStyle left;

        final CellStyle sumLabel;
        final CellStyle sumCell;
        final CellStyle sumRemain;

        final CellStyle pctLabel;
        final CellStyle pct;

        Styles(SXSSFWorkbook wb) {
            Font headerFont = wb.createFont();
            headerFont.setBold(true);

            headerBlue = createHeader(wb, headerFont, IndexedColors.LIGHT_CORNFLOWER_BLUE);
            headerGreen = createHeader(wb, headerFont, IndexedColors.LIGHT_GREEN);
            headerYellow = createHeader(wb, headerFont, IndexedColors.LIGHT_YELLOW);
            headerRed = createHeader(wb, headerFont, IndexedColors.ROSE);

            headerDefault = wb.createCellStyle();
            headerDefault.setFont(headerFont);
            headerDefault.setAlignment(HorizontalAlignment.CENTER);
            headerDefault.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerDefault);

            wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setAlignment(HorizontalAlignment.CENTER);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);

            redBold = wb.createCellStyle();
            redBold.cloneStyleFrom(wrap);
            Font redBoldFont = wb.createFont();
            redBoldFont.setBold(true);
            redBoldFont.setColor(IndexedColors.RED.getIndex());
            redBold.setFont(redBoldFont);

            cell = wb.createCellStyle();
            cell.setAlignment(HorizontalAlignment.CENTER);
            cell.setVerticalAlignment(VerticalAlignment.CENTER);
            cell.setWrapText(true);
            setBorder(cell);

            left = wb.createCellStyle();
            left.cloneStyleFrom(cell);
            left.setAlignment(HorizontalAlignment.LEFT);

            sumLabel = wb.createCellStyle();
            sumLabel.cloneStyleFrom(left);
            sumLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sumLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(sumLabel);

            sumCell = wb.createCellStyle();
            sumCell.cloneStyleFrom(cell);
            sumCell.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sumCell.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(sumCell);

            sumRemain = wb.createCellStyle();
            sumRemain.cloneStyleFrom(cell);
            sumRemain.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            sumRemain.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(sumRemain);

            pctLabel = wb.createCellStyle();
            pctLabel.cloneStyleFrom(left);
            pctLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            pctLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(pctLabel);

            pct = wb.createCellStyle();
            pct.cloneStyleFrom(cell);
            pct.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            pct.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            pct.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
            setBorder(pct);
        }

        private static CellStyle createHeader(SXSSFWorkbook wb, Font headerFont, IndexedColors color) {
            CellStyle s = wb.createCellStyle();
            s.setFont(headerFont);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setFillForegroundColor(color.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(s);
            return s;
        }

        private static void setBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }

    // =========================
    // Totals for summary sheet
    // =========================
    private class Totals {
        double allTtl=0, allI=0, allII=0, allIII=0, allIV=0, allV=0;
        double proTtl=0, proI=0, proII=0, proIII=0, proIV=0, proV=0;
        double remTtl=0, remI=0, remII=0, remIII=0, remIV=0, remV=0;
        double hseTtl=0, hseI=0, hseII=0, hseIII=0, hseIV=0, hseV=0;

        void add(DivisionSummaryDTO d) {
            allTtl += toDoubleSafe(d.getAllTtl());
            allI   += toDoubleSafe(d.getAllI());
            allII  += toDoubleSafe(d.getAllII());
            allIII += toDoubleSafe(d.getAllIII());
            allIV  += toDoubleSafe(d.getAllIV());
            allV   += toDoubleSafe(d.getAllV());

            proTtl += toDoubleSafe(d.getProDoneTtl());
            proI   += toDoubleSafe(d.getProDoneI());
            proII  += toDoubleSafe(d.getProDoneII());
            proIII += toDoubleSafe(d.getProDoneIII());
            proIV  += toDoubleSafe(d.getProDoneIV());
            proV   += toDoubleSafe(d.getProDoneV());

            remTtl += toDoubleSafe(d.getRemainTtl());
            remI   += toDoubleSafe(d.getRemainI());
            remII  += toDoubleSafe(d.getRemainII());
            remIII += toDoubleSafe(d.getRemainIII());
            remIV  += toDoubleSafe(d.getRemainIV());
            remV   += toDoubleSafe(d.getRemainV());

            hseTtl += toDoubleSafe(d.getHseDoneTtl());
            hseI   += toDoubleSafe(d.getHseDoneI());
            hseII  += toDoubleSafe(d.getHseDoneII());
            hseIII += toDoubleSafe(d.getHseDoneIII());
            hseIV  += toDoubleSafe(d.getHseDoneIV());
            hseV   += toDoubleSafe(d.getHseDoneV());
        }
    }

    // =========================
    // Helpers (cells, parsing)
    // =========================
    private void setText(Row r, int c, Object v, CellStyle style) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : String.valueOf(v));
        if (style != null) cell.setCellStyle(style);
    }

    private void setNumber(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private void setPercent(Row row, int col, double ratio, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(ratio);
        if (style != null) cell.setCellStyle(style);
    }

    private void setBlank(Row row, int col, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue("");
        if (style != null) cell.setCellStyle(style);
    }

    private void setRiskLevelText(Row r, int c, Object v, CellStyle normalStyle, CellStyle redBoldStyle) {
        Cell cell = r.createCell(c);
        String value = v == null ? "" : String.valueOf(v);
        cell.setCellValue(value);

        if ("IV".equalsIgnoreCase(value) || "V".equalsIgnoreCase(value)) {
            cell.setCellStyle(redBoldStyle);
        } else {
            cell.setCellStyle(normalStyle);
        }
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : DTF.format(t);
    }

    private List<String> clean(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
    }

    private String imgAt(List<String> list, int idx) {
        List<String> c = clean(list);
        if (idx < 0 || idx >= c.size()) return "";
        return c.get(idx);
    }

    private double toDoubleSafe(Number n) {
        return n == null ? 0d : n.doubleValue();
    }

    private double toDoubleSafe(String s) {
        if (s == null) return 0d;
        String x = s.trim();
        if (x.isEmpty()) return 0d;
        x = x.replace(",", "");
        try {
            return Double.parseDouble(x);
        } catch (Exception e) {
            return 0d;
        }
    }

    // =========================
    // Image
    // =========================
    private byte[] compressToJpegThumb(byte[] inputBytes) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(inputBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .size(IMG_MAX, IMG_MAX)
                    .outputFormat("jpg")
                    .outputQuality(IMG_QUALITY)
                    .toOutputStream(out);

            return out.toByteArray();
        }
    }

    private void addImageCell(
            Workbook wb,
            Sheet sheet,
            Drawing<?> drawing,
            CreationHelper helper,
            Row row,
            int col,
            String imageName
    ) {
        row.createCell(col); // keep layout

        if (imageName == null || imageName.isBlank()) return;

        try {
            String url = IMAGE_BASE_URL + imageName;

            byte[] original;
            try (InputStream in = new URL(url).openStream()) {
                original = IOUtils.toByteArray(in);
            }

            byte[] bytes = compressToJpegThumb(original);
            int pictureIdx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);

            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(col);
            anchor.setRow1(row.getRowNum());
            anchor.setCol2(col + 1);
            anchor.setRow2(row.getRowNum() + 1);

            int pad = 2 * 9525;
            anchor.setDx1(pad);
            anchor.setDy1(pad);
            anchor.setDx2(-pad);
            anchor.setDy2(-pad);

            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(anchor, pictureIdx);

            row.setHeightInPoints(Math.max(row.getHeightInPoints(), 80f));
            sheet.setColumnWidth(col, Math.max(sheet.getColumnWidth(col), 14 * 256));

        } catch (Exception ignored) {
            // nếu muốn debug: log.warn("Cannot load image {}", imageName, ignored);
        }
    }

    // keep your existing border helper if you still use it elsewhere
    @SuppressWarnings("unused")
    private void setHeaderBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
