package com.example.patrol_be.service;

import com.example.patrol_be.dto.PatrolReportDTO;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatrolReportExportExcelService {

    private final PatrolReportService patrolReportService;

    private final String imageBaseUrl = "http://192.168.122.15:9299/images/";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ✅ THUMB CONFIG (nhẹ nhất mà vẫn nhìn được)
    private static final int IMG_MAX = 200;     // 160-240 tuỳ bạn
    private static final float IMG_QUALITY = 0.55f;

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
            String qrKey
    ) throws Exception {

        List<PatrolReportDTO> rows = patrolReportService.search(
                plant, division, area, machine, type, afStatus, grp, pic, patrolUser, qrKey
        );

        try (SXSSFWorkbook  wb = new SXSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ✅ giảm dung lượng temp + zip tốt hơn (nhẹ hơn khi nhiều ảnh)
            wb.setCompressTempFiles(true);

            Sheet sheet = wb.createSheet("Patrol Reports");

            // ===== Styles =====
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle wrapStyle = wb.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            // ===== Header (ĐÚNG THỨ TỰ DTO) =====
            String[] headers = new String[]{
                    "id","stt","qr_key","type","grp","plant","division","area","machine","patrol_user",
                    "riskFreq","riskProb","riskSev","riskTotal",
                    "comment","countermeasure","checkInfo",
                    "imageNames","createdAt","pic","dueDate",
                    "at_imageNames","at_comment","at_date","at_pic","at_status",
                    "hse_judge","hse_imageNames","hse_comment","hse_date",
                    "load_status",
                    "BeforeImg(1)","AfterImg(1)","HSEImg(1)"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // ✅ CHỈ TẠO 1 LẦN
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();

            // ===== Data =====
            int rIndex = 1;
            for (PatrolReportDTO d : rows) {
                Row r = sheet.createRow(rIndex++);
                r.setHeightInPoints(70); // đủ cho thumbnail 200px theo cột ~16

                int col = 0;

                setText(r, col++, d.getId());
                setText(r, col++, d.getStt());
                setText(r, col++, d.getQr_key());
                setText(r, col++, d.getType());
                setText(r, col++, d.getGrp());
                setText(r, col++, d.getPlant());
                setText(r, col++, d.getDivision());
                setText(r, col++, d.getArea());
                setText(r, col++, d.getMachine());
                setText(r, col++, d.getPatrol_user());

                setText(r, col++, d.getRiskFreq());
                setText(r, col++, d.getRiskProb());
                setText(r, col++, d.getRiskSev());
                setText(r, col++, d.getRiskTotal());

                setTextWrap(r, col++, d.getComment(), wrapStyle);
                setTextWrap(r, col++, d.getCountermeasure(), wrapStyle);
                setTextWrap(r, col++, d.getCheckInfo(), wrapStyle);

                setTextWrap(r, col++, joinList(d.getImageNames()), wrapStyle);
                setText(r, col++, fmt(d.getCreatedAt()));
                setText(r, col++, d.getPic());
                setText(r, col++, fmt(d.getDueDate()));

                setTextWrap(r, col++, joinList(d.getAt_imageNames()), wrapStyle);
                setTextWrap(r, col++, d.getAt_comment(), wrapStyle);
                setText(r, col++, fmt(d.getAt_date()));
                setText(r, col++, d.getAt_pic());
                setText(r, col++, d.getAt_status());

                setText(r, col++, d.getHse_judge());
                setTextWrap(r, col++, joinList(d.getHse_imageNames()), wrapStyle);
                setTextWrap(r, col++, d.getHse_comment(), wrapStyle);
                setText(r, col++, fmt(d.getHse_date()));

                setText(r, col++, d.getLoad_status());

                // ✅ NHÚNG THUMBNAIL (nhẹ nhất)
                addImageCell(wb, sheet, drawing, helper, r, col++, first(d.getImageNames()));      // Before
                addImageCell(wb, sheet, drawing, helper, r, col++, first(d.getAt_imageNames()));   // After
                addImageCell(wb, sheet, drawing, helper, r, col++, first(d.getHse_imageNames()));  // HSE
            }

            // ===== Column width =====
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, 18 * 256);

            sheet.setColumnWidth(14, 40 * 256); // comment
            sheet.setColumnWidth(15, 40 * 256); // countermeasure
            sheet.setColumnWidth(16, 35 * 256); // checkInfo

            sheet.setColumnWidth(17, 35 * 256); // imageNames
            sheet.setColumnWidth(21, 35 * 256); // at_imageNames
            sheet.setColumnWidth(27, 35 * 256); // hse_imageNames

            // cột ảnh: nhỏ hơn để nhẹ
            sheet.setColumnWidth(31, 14 * 256);
            sheet.setColumnWidth(32, 14 * 256);
            sheet.setColumnWidth(33, 14 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ===== helpers =====

    private void setText(Row r, int c, Object v) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : String.valueOf(v));
    }

    private void setTextWrap(Row r, int c, String v, CellStyle wrapStyle) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        cell.setCellStyle(wrapStyle);
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : DTF.format(t);
    }

    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(", ", list);
    }

    private String first(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        for (String s : list) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
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
        // tạo cell để giữ layout
        row.createCell(col);

        if (imageName == null || imageName.isBlank()) return;

        try {
            String url = imageBaseUrl + imageName;

            byte[] original;
            try (InputStream in = new URL(url).openStream()) {
                original = IOUtils.toByteArray(in);
            }

            // thumbnail JPEG nhẹ
            byte[] bytes = compressToJpegThumb(original);

            int pictureIdx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);

            ClientAnchor anchor = helper.createClientAnchor();

            // neo trong đúng 1 ô: [col,row] -> [col+1,row+1]
            anchor.setCol1(col);
            anchor.setRow1(row.getRowNum());
            anchor.setCol2(col + 1);
            anchor.setRow2(row.getRowNum() + 1);

            // padding trong ô (đơn vị EMU)
            // 1 px ≈ 9525 EMU
            int pad = 2 * 9525;
            anchor.setDx1(pad);
            anchor.setDy1(pad);
            anchor.setDx2(-pad);
            anchor.setDy2(-pad);

            // để ảnh "move+resize theo cell" nếu user kéo giãn hàng/cột
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

            drawing.createPicture(anchor, pictureIdx);

            // ✅ set kích thước ô cho ảnh (đừng set mỗi lần nếu bạn đã set cột ảnh cố định ở ngoài)
            // Row height: 80 points ~ vừa với thumbnail 200px trong cột ~14
            row.setHeightInPoints(Math.max(row.getHeightInPoints(), 80f));

            // cột ảnh đủ rộng
            sheet.setColumnWidth(col, Math.max(sheet.getColumnWidth(col), 14 * 256));

        } catch (Exception ignored) {
            // bỏ qua để không fail export
        }
    }


}
