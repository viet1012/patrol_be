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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ExcelService {


    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
//    private static final Path BASE_DIR = Path.of("C:/Users/KVH_IT_SM_Ngu/OneDrive - MISUMI Group Inc/patrol_excel");
    private static final String EXCEL_FILE_NAME = "reports.xlsx";
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";

    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);

    //ngu.nguyen 251211
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)  // Force HTTP/1.1 to avoid HTTP/2 upgrade bug
            .build();
    @Value("${lm.url:http://192.168.122.16:1234}") // m?c d?nh IP b?n cung c?p
    public String lmUrl;
    @Value("${lm.apiKey:}") // n?u LM Studio c?n api key thì set vào properties, n?u không d? tr?ng
    public String lmApiKey;
    //ngu.nguyen 251211 end

//    private static final Path BASE_DIR = Paths.get("C:\\Users\\diep.nguyen\\OneDrive - ?????????????");
//    private static final String EXCEL_FILE_NAME = "reports.xlsx";
//    private static final String IMAGE_FOLDER_NAME = "uploaded_images";
//
//    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
//    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);


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

        // ngu.nguyen 251211
        try {
            String comment = req.getComment();
            if (comment != null && !comment.isBlank()) {
                String translated = translateLLM(comment);
                if (translated != null && !translated.isBlank()) {
                    req.setComment(comment + "\n" + translated);
                    System.out.println("comment: " + comment + "--" + translated);
                }
            }
        } catch (Exception ex) {
            // log nhung không d?ng luu excel
            ex.printStackTrace();
        }
        try {
            String counter = req.getCountermeasure();
            if (counter != null && !counter.isBlank()) {
                String translatedCounter = translateLLM(counter);
                if (translatedCounter != null && !translatedCounter.isBlank()) {
                    req.setCountermeasure(counter + "\n" + translatedCounter);
                    System.out.println("countermeasure: " + counter + "--" + translatedCounter);
                }
            }
        } catch (Exception ex) {
            // log nhung không d?ng luu excel
            ex.printStackTrace();
        }
        // ngu.nguyen 251211 end


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
                "Risk FREQ", "Risk PROB", "Risk SEV", "Risk Level",
                "Content", "Countermeasure", "Check Similar",
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
        //System.out.println("Plant: " + s(req.getPlant()));
        col++;

        row.createCell(col).setCellValue(s(req.getGroup()));
        //System.out.println("Group: " + s(req.getGroup()));
        col++;

        row.createCell(col).setCellValue(s(req.getDivision()));
        //System.out.println("Division: " + s(req.getDivision()));
        col++;

        row.createCell(col).setCellValue(s(req.getArea()));
        //System.out.println("Area: " + s(req.getArea()));
        col++;

        row.createCell(col).setCellValue(s(req.getMachine()));
        //System.out.println("Machine: " + s(req.getMachine()));
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

        row.createCell(col).setCellValue(s(req.getCountermeasure()));
        col++;

        row.createCell(col).setCellValue(s(req.getCheck()));
    }



    private void insertImages(Workbook workbook, Sheet sheet, int rowNum, List<String> images) {
        if (images.isEmpty()) return;

        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        for (int i = 0; i < images.size(); i++) {
            int col = 13 + i;
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

    public String translateLLM(String orgText) throws IOException, InterruptedException {
        if (orgText == null || orgText.isBlank()) return orgText;

        // System prompt
//        String systemPrompt =
//                "You are a professional translator specializing in factory safety patrols and 5S management in mechanical manufacturing plants. "
//                        + "The input text is a comment from a safety inspection or 5S audit report. "
//                        + "Detect the primary language of the input text: "
//                        + "If it's Vietnamese, translate it to natural Japanese. "
//                        + "If it's Japanese, translate it to natural Vietnamese. "
//                        + "If it's neither or unclear, return the original text unchanged. "
//                        + "Return ONLY the translated text (or original if no translation). "
//                        + "Use newline (\\n) where appropriate in the output. "
//                        + "Do not include explanations, labels, or the original text.";

//        String systemPrompt =
//                "You are a professional translator specializing in factory safety patrols and 5S management. "
//                        + "The input text may be Vietnamese (with or without diacritics) or Japanese. "
//                        + "First, detect the primary language: "
//                        + "- If the text is Vietnamese WITHOUT diacritics, restore proper Vietnamese diacritics first. "
//                        + "- If the text is Vietnamese WITH diacritics, use it as is. "
//                        + "- If the text is Japanese, keep it. "
//                        + "After restoring the correct Vietnamese form (if needed), translate: "
//                        + "- If Vietnamese → translate to natural Japanese. "
//                        + "- If Japanese → translate to natural Vietnamese. "
//                        + "If the language is neither or unclear, return the original text unchanged. "
//                        + "Return ONLY the translated text (or original if no translation). "
//                        + "Do not include explanations or labels.";
        String systemPrompt =
                "You are a professional translator specializing in factory safety patrols, 5S audits, and mechanical manufacturing environments.\n" +
                        "The input text is a comment from a safety/5S inspection report and can be in one of these languages:\n" +
                        "• Vietnamese (with or without diacritics)\n" +
                        "• Japanese\n" +
                        "• English\n" +
                        "• Or a mix of the above\n\n" +

                        "Follow these rules exactly:\n" +
                        "1. First, detect the primary language of the input.\n" +
                        "2. If the text is Vietnamese WITHOUT proper diacritics (e.g. 'kiem tra may moc'), restore it to correct Vietnamese with full diacritics first.\n" +
                        "3. Translation rules:\n" +
                        "   • If the primary language is Japanese → translate to natural, accurate Vietnamese (with correct diacritics).\n" +
                        "   • If the primary language is ANYTHING ELSE (Vietnamese with/without diacritics, English, mixed, etc.) → translate to natural, professional Japanese used in Japanese manufacturing factories.\n" +
                        "4. Preserve all technical terms related to safety, 5S (整理・整頓・清掃・清潔・躾), machinery, risk levels, production areas, tools, etc.\n" +
                        "5. Return ONLY the final translated text. Do not include explanations, labels, quotes, original text, or language names.\n" +
                        "6. Use newlines (\\n) to maintain the original formatting when needed.";


        // JSON payload
        ObjectNode payload = mapper.createObjectNode();
        payload.put("com/example/patrol_be/model", "openai/gpt-oss-20b");
        payload.put("temperature", 0.2);
        payload.put("max_tokens", 2000);
        System.out.println("Payload: " + mapper.writeValueAsString(payload)); // Log the exact JSON

        ArrayNode messages = payload.putArray("messages");

        ObjectNode sysMsg = mapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", orgText);
        messages.add(userMsg);

        // Endpoint chu?n LM Studio
        String endpoint = lmUrl.endsWith("/")
                ? lmUrl + "v1/chat/completions"
                : lmUrl + "/v1/chat/completions";
        System.out.println("Calling endpoint: " + endpoint);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));

        // N?u server có dùng API Key thì g?n vào (da s? LM Studio không c?n)
        if (lmApiKey != null && !lmApiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + lmApiKey);
        }
        //System.out.println("Before send");

        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");

            if (!content.isMissingNode()) {
                return content.asText().trim();
            }
        } else {
            throw new IOException("LM Studio error " + response.statusCode() + ": " + response.body());
        }
        System.out.println("After send - Status: " + response.statusCode());

        return orgText; // fallback
    }
}
