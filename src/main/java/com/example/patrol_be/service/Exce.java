package com.example.patrol_be.service;

import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.PatrolReportRepo;

import com.example.patrol_be.ReportRequest;
import com.example.patrol_be.repository.PatrolReportRepo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

@Service
@RequiredArgsConstructor
public class Exce {

    private final PatrolReportRepo reportRepo;
    private final SttService sttService;

    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String EXCEL_FILE_NAME = "reports.xlsx";
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";

    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);

    // LLM
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${lm.url:http://192.168.122.16:1234}")
    private String lmUrl;

    @Value("${lm.apiKey:}")
    private String lmApiKey;



    private Path getExcelFilePathByPlant(String plant) {
        String safePlant = plant == null ? "default" : plant.replaceAll("\\W+", "_");
        String fileName = "Safety_Patrol_" + safePlant + ".xlsx";
        return BASE_DIR.resolve(fileName);
    }

    // ===========================
    // MAIN FUNCTION
    // ===========================
    public synchronized void appendToExcel(ReportRequest req, MultipartFile[] images) throws IOException {

        if (!Files.exists(imageFolderPath)) Files.createDirectories(imageFolderPath);

        // 1) Lấy STT theo group
        int stt = sttService.nextByFac(
                req.getPlant()
        );
        req.setStt(stt);

        // 2) Lưu ảnh
        List<String> savedImageNames = saveImageFiles(images);
        req.setImageFileNames(savedImageNames);

        // 3) Dịch comment / countermeasure
        try {
            if (req.getComment() != null && !req.getComment().isBlank()) {
                String translated = translateLLM(req.getComment());
                if (translated != null) {
                    req.setComment(req.getComment() + "\n" + translated);
                }
            }

            if (req.getCountermeasure() != null && !req.getCountermeasure().isBlank()) {
                String translated = translateLLM(req.getCountermeasure());
                if (translated != null) {
                    req.setCountermeasure(req.getCountermeasure() + "\n" + translated);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 4) Lưu vào database
        PatrolReport rpt = new PatrolReport();
        rpt.setStt(stt);
        String grp = req.getGroup().replace(" ", "").trim();
        rpt.setGrp(grp);
        rpt.setPlant(req.getPlant());
        rpt.setDivision(req.getDivision());
        rpt.setArea(req.getArea());
        rpt.setMachine(req.getMachine());

        rpt.setRiskFreq(req.getRiskFreq());
        rpt.setRiskProb(req.getRiskProb());
        rpt.setRiskSev(req.getRiskSev());
        rpt.setRiskTotal(req.getRiskTotal());

        rpt.setComment(req.getComment());
        rpt.setCountermeasure(req.getCountermeasure());
        rpt.setCheckInfo(req.getCheck());
        rpt.setImageNames(String.join(",", savedImageNames));

        reportRepo.save(rpt);

        // 5) Ghi Excel
        // Xử lý file Excel theo plant
        Path excelFilePath = getExcelFilePathByPlant(req.getPlant());
        
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

        saveWorkbook(workbook,excelFilePath);
        workbook.close();
    }

    // ===========================
    // SAVE IMAGES
    // ===========================
    private List<String> saveImageFiles(MultipartFile[] images) throws IOException {
        List<String> list = new ArrayList<>();
        if (images == null) return list;

        for (MultipartFile f : images) {
            if (f.isEmpty()) continue;

            String ext = ".jpg";
            if (f.getOriginalFilename() != null && f.getOriginalFilename().contains(".")) {
                ext = f.getOriginalFilename().substring(f.getOriginalFilename().lastIndexOf("."));
            }

            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
            Path savePath = imageFolderPath.resolve(fileName);

            f.transferTo(savePath.toFile());
            list.add(fileName);
        }
        return list;
    }

    // ===========================
    // HEADER
    // ===========================
    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);

        String[] cols = {
                "Time", "STT", "Plant", "Group", "Division", "Area", "Machine",
                "Risk FREQ", "Risk PROB", "Risk SEV", "Risk Level",
                "Content", "Countermeasure", "Check Similar",
                "Image 1", "Image 2", "Image 3", "Image 4", "Image 5"
        };

        for (int i = 0; i < cols.length; i++) {
            sheet.setColumnWidth(i, 25 * 256);
            header.createCell(i).setCellValue(cols[i]);
        }
    }

    // ===========================
    // WRITE CELLS
    // ===========================
    private void writeTextCells(Row row, ReportRequest req) {
        int col = 0;

        row.createCell(col++).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        row.createCell(col++).setCellValue(req.getStt());
        row.createCell(col++).setCellValue(s(req.getPlant()));
        row.createCell(col++).setCellValue(s(req.getGroup()));
        row.createCell(col++).setCellValue(s(req.getDivision()));
        row.createCell(col++).setCellValue(s(req.getArea()));
        row.createCell(col++).setCellValue(s(req.getMachine()));

        row.createCell(col++).setCellValue(s(req.getRiskFreq()));
        row.createCell(col++).setCellValue(s(req.getRiskProb()));
        row.createCell(col++).setCellValue(s(req.getRiskSev()));
        row.createCell(col++).setCellValue(s(req.getRiskTotal()));

        row.createCell(col++).setCellValue(s(req.getComment()));
        row.createCell(col++).setCellValue(s(req.getCountermeasure()));
        row.createCell(col++).setCellValue(s(req.getCheck()));
    }

    // ===========================
    // IMAGE INSERT
    // ===========================
    private void insertImages(Workbook workbook, Sheet sheet, int rowNum, List<String> images) {
        if (images.isEmpty()) return;

        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        for (int i = 0; i < images.size(); i++) {
            int col = 14 + i;
            Path p = imageFolderPath.resolve(images.get(i));

            try (InputStream is = Files.newInputStream(p)) {
                byte[] bytes = IOUtils.toByteArray(is);
                int pictIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);

                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(col);
                anchor.setRow1(rowNum);
                anchor.setCol2(col + 1);
                anchor.setRow2(rowNum + 1);
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

                Picture picture = drawing.createPicture(anchor, pictIdx);
                picture.resize(1.0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ===========================
    // SAVE EXCEL
    // ===========================
    private void saveWorkbook(Workbook workbook, Path path) throws IOException {
        try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            workbook.write(os);
        }
    }


    private String s(String v) {
        return v == null ? "" : v;
    }

    // ===========================
    // LLM TRANSLATE
    // ===========================
    public String translateLLM(String text) throws IOException, InterruptedException {
        if (text == null || text.isBlank()) return text;

        String systemPrompt =
                "Translate 5S/safety patrol comments between Vietnamese ↔ Japanese automatically.\n"
                        + "Detect language first.\n"
                        + "If Japanese → translate to Vietnamese.\n"
                        + "Else → translate to natural Japanese.\n"
                        + "Return ONLY the translation.";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", "openai/gpt-oss-20b");
        payload.put("temperature", 0.2);
        payload.put("max_tokens", 2000);

        ArrayNode messages = payload.putArray("messages");

        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        ObjectNode usr = mapper.createObjectNode();
        usr.put("role", "user");
        usr.put("content", text);
        messages.add(usr);

        String endpoint = lmUrl + "/v1/chat/completions";

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        if (lmApiKey != null && !lmApiKey.isBlank()) {
            req.header("Authorization", "Bearer " + lmApiKey);
        }

        HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            JsonNode root = mapper.readTree(resp.body());
            String result = root.path("choices").path(0).path("message").path("content").asText();
            return result.trim();
        }

        return text;
    }
}
