package com.example.patrol_be.service;

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

    private final String imageBaseUrl = "http://192.168.122.15:9299/images/";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Thumb
    private static final int IMG_MAX = 200;
    private static final float IMG_QUALITY = 0.55f;

    // ✅ bạn muốn cố định 3 ảnh mỗi nhóm
    private static final int CAP = 3;

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
                plant, division, area, machine, type, afStatus, grp, pic, patrolUser, qrKey,from,to
        );
        System.out.println("rows: " + rows.size());
        try (SXSSFWorkbook wb = new SXSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            wb.setCompressTempFiles(true);

            Sheet sheet = wb.createSheet("Patrol Reports");


//          ===== Header base =====
            Font headerFont = wb.createFont();
            headerFont.setBold(true);

// 🔵 Patrol / Before
            CellStyle headerBlue = wb.createCellStyle();
            headerBlue.setFont(headerFont);
            headerBlue.setAlignment(HorizontalAlignment.CENTER);
            headerBlue.setVerticalAlignment(VerticalAlignment.CENTER);
            headerBlue.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerBlue.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setHeaderBorder(headerBlue);

// 🟢 PIC / After
            CellStyle headerGreen = wb.createCellStyle();
            headerGreen.setFont(headerFont);
            headerGreen.setAlignment(HorizontalAlignment.CENTER);
            headerGreen.setVerticalAlignment(VerticalAlignment.CENTER);
            headerGreen.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            headerGreen.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setHeaderBorder(headerGreen);

// 🟡 HSE
            CellStyle headerYellow = wb.createCellStyle();
            headerYellow.setFont(headerFont);
            headerYellow.setAlignment(HorizontalAlignment.CENTER);
            headerYellow.setVerticalAlignment(VerticalAlignment.CENTER);
            headerYellow.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            headerYellow.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setHeaderBorder(headerYellow);

// ⚪ Default header
            CellStyle headerDefault = wb.createCellStyle();
            headerDefault.setFont(headerFont);
            headerDefault.setAlignment(HorizontalAlignment.CENTER);
            headerDefault.setVerticalAlignment(VerticalAlignment.CENTER);
            setHeaderBorder(headerDefault);

            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setAlignment(HorizontalAlignment.CENTER);
            wrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 🔴 Style cho IV / V
            CellStyle redBoldStyle = wb.createCellStyle();
            redBoldStyle.cloneStyleFrom(wrapStyle); // giữ wrap + align

            Font redBoldFont = wb.createFont();
            redBoldFont.setBold(true);
            redBoldFont.setColor(IndexedColors.RED.getIndex());
            redBoldStyle.setFont(redBoldFont);

            // ✅ HEADER CỐ ĐỊNH ĐÚNG THỨ TỰ BẠN MUỐN
            String[] headers = {
                    "Report ID",
                    "No.",
                    "QR Key",
                    "Type",
                    "Group",
                    "Plant",
                    "Division",
                    "Area",
                    "Machine",

                    "Risk Frequency",
                    "Risk Probability",
                    "Risk Severity",
                    "Risk Score",



                    "Comment",
                    "Countermeasure",
                    "Check Information",
                    "Patrol User",

                    "Before Image 1",
                    "Before Image 2",
                    "Before Image 3",
                    "Created At",
                    "PIC",
                    "Due Date",

                    "After Image 1",
                    "After Image 2",
                    "After Image 3",

                    "After Comment",
                    "After Date",
                    "After PIC",
                    "After Status",

                    "HSE Decision",
                    "HSE Image 1",
                    "HSE Image 2",
                    "HSE Image 3",
                    "HSE Comment",
                    "HSE Date",

                    "Load Status"
            };


            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);

            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);

                // 🟦 Patrol / Before
                if (i == 16 || (i >= 17 && i <= 19) || i == 20) {
                    c.setCellStyle(headerBlue);
                }
                // 🟩 PIC / After
                else if (i == 21 || i == 22 || (i >= 23 && i <= 29)) {
                    c.setCellStyle(headerGreen);
                }
                // 🟨 HSE
                else if (i >= 30 && i <= 35) {
                    c.setCellStyle(headerYellow);
                }
                // ⚪ Default
                else {
                    c.setCellStyle(headerDefault);
                }
            }
            sheet.setAutoFilter(
                    new CellRangeAddress(
                            0,                  // header row start
                            0,                  // header row end
                            0,                  // first column
                            headers.length - 1  // last column
                    )
            );

            // ✅ chỉ tạo 1 lần
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();

            // ===== Data =====
            int rIndex = 1;
            for (PatrolReportDTO d : rows) {
                Row r = sheet.createRow(rIndex++);

                // ⚠️ KHÔNG fix height cứng nếu muốn wrap thấy rõ
                r.setHeight((short) -1); // Excel auto height khi mở file

                int col = 0;

                setText(r, col++, d.getId(), wrapStyle);
                setText(r, col++, d.getStt(), wrapStyle);
                setText(r, col++, d.getQr_key(), wrapStyle);
                setText(r, col++, d.getType(), wrapStyle);
                setText(r, col++, d.getGrp(), wrapStyle);
                setText(r, col++, d.getPlant(), wrapStyle);
                setText(r, col++, d.getDivision(), wrapStyle);
                setText(r, col++, d.getArea(), wrapStyle);
                setText(r, col++, d.getMachine(), wrapStyle);

                setText(r, col++, d.getRiskFreq(), wrapStyle);
                setText(r, col++, d.getRiskProb(), wrapStyle);
                setText(r, col++, d.getRiskSev(), wrapStyle);
                setRiskLevelText(r, col++, d.getRiskTotal(), wrapStyle, redBoldStyle);



                setText(r, col++, d.getComment(), wrapStyle);
                setText(r, col++, d.getCountermeasure(), wrapStyle);
                setText(r, col++, d.getCheckInfo(), wrapStyle);
                setText(r, col++, d.getPatrol_user(), wrapStyle);

                // BEFORE IMG
                for (int i = 0; i < CAP; i++) {
                    addImageCell(wb, sheet, drawing, helper, r, col++, imgAt(d.getImageNames(), i));
                }
                setText(r, col++, fmt(d.getCreatedAt()), wrapStyle);
                setText(r, col++, d.getPic(), wrapStyle);
                setText(r, col++, fmt(d.getDueDate()), wrapStyle);

                // AFTER IMG
                for (int i = 0; i < CAP; i++) {
                    addImageCell(wb, sheet, drawing, helper, r, col++, imgAt(d.getAt_imageNames(), i));
                }

                setText(r, col++, d.getAt_comment(), wrapStyle);
                setText(r, col++, fmt(d.getAt_date()), wrapStyle);
                setText(r, col++, d.getAt_pic(), wrapStyle);
                setText(r, col++, d.getAt_status(), wrapStyle);

                setText(r, col++, d.getHse_judge(), wrapStyle);

                setText(r, col++, imgAt(d.getHse_imageNames(), 0), wrapStyle);
                setText(r, col++, imgAt(d.getHse_imageNames(), 1), wrapStyle);
                setText(r, col++, imgAt(d.getHse_imageNames(), 2), wrapStyle);

                setText(r, col++, d.getHse_comment(), wrapStyle);
                setText(r, col++, fmt(d.getHse_date()), wrapStyle);

                setText(r, col++, d.getLoad_status(), wrapStyle);
            }


            // ===== Column width =====
            // default
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }

            // text rộng hơn
            // comment/countermeasure/checkInfo
            sheet.setColumnWidth(0, 10 * 256); // comment
            sheet.setColumnWidth(1, 10 * 256); // comment
            sheet.setColumnWidth(2, 10 * 256); // comment

            sheet.setColumnWidth(13, 40 * 256); // comment
            sheet.setColumnWidth(14, 40 * 256); // countermeasure
            sheet.setColumnWidth(15, 40 * 256); // checkInfo
            sheet.setColumnWidth(26, 40 * 256); // at_comment

            // hse_comment
            sheet.setColumnWidth(34, 40 * 256);

            // ảnh: set nhỏ gọn cho 1 cell
//            // BeforeImg(1..3) nằm ở cột 17..19
//            for (int c = 17; c <= 19; c++) sheet.setColumnWidth(c, 14 * 256);
//            // AfterImg(1..3) nằm ở cột 23..25
//            for (int c = 23; c <= 25; c++) sheet.setColumnWidth(c, 14 * 256);

            // hse_imageNames1..3 là text tên ảnh => để vừa vừa
            sheet.setColumnWidth(31, 22 * 256);
            sheet.setColumnWidth(32, 22 * 256);
            sheet.setColumnWidth(33, 22 * 256);


            wb.write(out);
            return out.toByteArray();
        }
    }

    // ===== helpers =====
    private String normalize(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private void setHeaderBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void setText(Row r, int c, Object v, CellStyle wrapStyle) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : String.valueOf(v));
        cell.setCellStyle(wrapStyle);
    }

    private void setRiskLevelText(
            Row r,
            int c,
            Object v,
            CellStyle normalStyle,
            CellStyle redBoldStyle
    ) {
        Cell cell = r.createCell(c);

        String value = v == null ? "" : String.valueOf(v);
        cell.setCellValue(value);

        // ✅ Check IV / V
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

    // ✅ resize + nén mạnh về JPEG thumbnail
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
        row.createCell(col); // giữ layout

        if (imageName == null || imageName.isBlank()) return;

        try {
            String url = imageBaseUrl + imageName;

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

            // padding trong ô
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
        }
    }
}
