package com.example.patrol_be;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ExcelService {

    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String EXCEL_FILE_NAME = "reports.xlsx";
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";

    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);

    public synchronized void appendToExcel(ReportRequest req, MultipartFile[] images) throws IOException {

        if (!Files.exists(imageFolderPath)) Files.createDirectories(imageFolderPath);

        List<String> savedImageNames = saveImageFiles(images);

        Workbook workbook;
        Sheet sheet;

        if (Files.notExists(excelFilePath)) {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("Reports");
            createHeader(sheet);
        } else {
            try (InputStream is = Files.newInputStream(excelFilePath)) {
                workbook = new XSSFWorkbook(is);
                sheet = workbook.getSheetAt(0);
            }
        }

        int rowNum = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(rowNum);
        row.setHeightInPoints(140);

        writeTextCells(row, req);
        insertImages(workbook, sheet, rowNum, savedImageNames);

        saveWorkbook(workbook);
        workbook.close();
    }


    private List<String> saveImageFiles(MultipartFile[] images) throws IOException {
        List<String> result = new ArrayList<>();
        if (images == null) return result;

        for (MultipartFile file : images) {
            if (file.isEmpty() || result.size() >= 5) continue;

            String ext = ".jpg";
            String name = file.getOriginalFilename();
            if (name != null && name.contains(".")) {
                ext = name.substring(name.lastIndexOf("."));
            }

            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
            Path imgPath = imageFolderPath.resolve(fileName);
            file.transferTo(imgPath.toFile());
            result.add(fileName);
        }
        return result;
    }


    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] cols = {
                "Time",
                "Plant", "Group", "Division", "Area", "Machine",
                "Risk FREQ", "Risk PROB", "Risk SEV", "Risk TOTAL",
                "Comment", "Check",
                "Image 1", "Image 2", "Image 3", "Image 4", "Image 5"
        };

        for (int i = 0; i < cols.length; i++) {
            sheet.setColumnWidth(i, 25 * 256);
            header.createCell(i).setCellValue(cols[i]);
        }
    }

    private void writeTextCells(Row row, ReportRequest req) {
        int col = 0;
        row.createCell(col++).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        row.createCell(col).setCellValue(s(req.getPlant()));
        System.out.println("Plant: " + s(req.getPlant()));
        col++;

        row.createCell(col).setCellValue(s(req.getGroup()));
        System.out.println("Group: " + s(req.getGroup()));
        col++;

        row.createCell(col).setCellValue(s(req.getDivision()));
        System.out.println("Division: " + s(req.getDivision()));
        col++;

        row.createCell(col).setCellValue(s(req.getArea()));
        System.out.println("Area: " + s(req.getArea()));
        col++;

        row.createCell(col).setCellValue(s(req.getMachine()));
        System.out.println("Machine: " + s(req.getMachine()));
        col++;

        row.createCell(col).setCellValue(s(req.getRiskFreq()));
        col++;

        row.createCell(col).setCellValue(s(req.getRiskProb()));
        col++;

        row.createCell(col).setCellValue(s(req.getRiskSev()));
        col++;

        row.createCell(col).setCellValue(req.getRiskTotal());
        col++;

        row.createCell(col).setCellValue(s(req.getComment()));
        col++;

        row.createCell(col).setCellValue(s(req.getCheck()));
    }



    private void insertImages(Workbook workbook, Sheet sheet, int rowNum, List<String> images) {
        if (images.isEmpty()) return;

        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        for (int i = 0; i < images.size(); i++) {
            int col = 12 + i;
            Path imgPath = imageFolderPath.resolve(images.get(i));

            try (InputStream is = Files.newInputStream(imgPath)) {
                byte[] bytes = IOUtils.toByteArray(is);
                int pictureIdx = workbook.addPicture(bytes, imageType(images.get(i)));

                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(col);
                anchor.setRow1(rowNum);
                anchor.setCol2(col + 1);
                anchor.setRow2(rowNum + 1);
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

                Picture picture = drawing.createPicture(anchor, pictureIdx);
                picture.resize(1.0);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    private void saveWorkbook(Workbook workbook) throws IOException {
        try (OutputStream os = Files.newOutputStream(excelFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            workbook.write(os);
        }
        System.out.println("Excel updated successfully!");
    }

    private String s(String v) { return v == null ? "" : v; }

    private int imageType(String f) {
        f = f.toLowerCase();
        if (f.endsWith(".png")) return Workbook.PICTURE_TYPE_PNG;
        return Workbook.PICTURE_TYPE_JPEG;
    }
}
