package com.example.patrol_be;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ExcelService {

    // Đường dẫn base (working dir), bạn có thể đổi thành config hoặc env var
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));

    private static final String EXCEL_FILE_NAME = "reports.xlsx";
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";

    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);

    public synchronized void appendToExcel(ReportRequest req, MultipartFile imageFile) throws IOException {
        // Tạo thư mục lưu ảnh nếu chưa tồn tại
        if (!Files.exists(imageFolderPath)) {
            Files.createDirectories(imageFolderPath);
            System.out.println("Created image folder at: " + imageFolderPath.toAbsolutePath());
        }

        String imageFileName = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            // Tạo tên file ảnh duy nhất: timestamp + UUID + gốc tên file
            String originalName = imageFile.getOriginalFilename();
            String extension = "";

            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            imageFileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;

            Path imagePath = imageFolderPath.resolve(imageFileName);
            // Lưu ảnh vào ổ đĩa
            imageFile.transferTo(imagePath.toFile());
            System.out.println("Saved image file: " + imagePath.toAbsolutePath());
        }

        Workbook workbook;
        Sheet sheet;

        if (Files.notExists(excelFilePath)) {
            // Tạo mới workbook và sheet, header
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("Reports");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Time");
            header.createCell(1).setCellValue("Division");
            header.createCell(2).setCellValue("Group");     // <-- thêm cột Group
            header.createCell(3).setCellValue("Machine");
            header.createCell(4).setCellValue("Comment");
            header.createCell(5).setCellValue("Reason 1");
            header.createCell(6).setCellValue("Reason 2");
            header.createCell(7).setCellValue("Image");
        } else {
            try (InputStream is = Files.newInputStream(excelFilePath)) {
                workbook = new XSSFWorkbook(is);
                sheet = workbook.getSheetAt(0);
            }
        }

        int rowNum = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(rowNum);

        row.createCell(0).setCellValue(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        row.createCell(1).setCellValue(safeString(req.getDivision()));
        row.createCell(2).setCellValue(safeString(req.getGroup()));    // <-- ghi dữ liệu Group
        row.createCell(3).setCellValue(safeString(req.getMachine()));
        row.createCell(4).setCellValue(safeString(req.getComment()));
        row.createCell(5).setCellValue(safeString(req.getReason1()));
        row.createCell(6).setCellValue(safeString(req.getReason2()));

        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        if (imageFileName != null) {
            Path imagePath = imageFolderPath.resolve(imageFileName);
            try (InputStream is = Files.newInputStream(imagePath)) {
                byte[] bytes = org.apache.poi.util.IOUtils.toByteArray(is);

                int pictureType = getPictureType(imageFileName);

                int pictureIdx = workbook.addPicture(bytes, pictureType);

                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(7);      // cột ảnh dịch sang phải 1
                anchor.setRow1(rowNum);
                anchor.setCol2(8);
                anchor.setRow2(rowNum + 1);

                Picture pict = drawing.createPicture(anchor, pictureIdx);
                pict.resize(1.0);
            } catch (IOException e) {
                System.err.println("Failed to insert image to Excel: " + e.getMessage());
                // fallback: ghi text đường dẫn ảnh
                row.createCell(7).setCellValue(imageFileName);
            }
        } else {
            // Không có ảnh thì để trống ô ảnh
            row.createCell(7).setCellValue("");
        }

        // Ghi file Excel
        try (OutputStream os = Files.newOutputStream(excelFilePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            workbook.write(os);
        }

        workbook.close();

        System.out.println("Append to Excel: SUCCESS at " + LocalDateTime.now());
    }

    private int getPictureType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return Workbook.PICTURE_TYPE_JPEG;
        if (lower.endsWith(".bmp")) return Workbook.PICTURE_TYPE_DIB;

        // Default fallback
        return Workbook.PICTURE_TYPE_JPEG;
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }
}
