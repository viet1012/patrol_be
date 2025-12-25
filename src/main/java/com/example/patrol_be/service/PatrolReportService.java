package com.example.patrol_be.service;

import com.example.patrol_be.dto.AtUpdateDTO;
import com.example.patrol_be.dto.PatrolReportDTO;
import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.PatrolReportRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatrolReportService {

    private final PatrolReportRepo repo;
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);


    public List<PatrolReportDTO> search(
            String division,
            String area,
            String machine,
            String type
    ) {
        return repo.search(
                        normalize(division),
                        normalize(area),
                        normalize(machine),
                        normalize(type)
                ).stream()
                .map(this::mapToDto)
                .toList();
    }

    private PatrolReportDTO mapToDto(Object[] r) {
        return new PatrolReportDTO(
                ((Number) r[0]).intValue(),              // id
                ((Number) r[1]).intValue(),              // stt
                (String) r[2],                           // type
                (String) r[3],                           // grp
                (String) r[4],                           // plant
                (String) r[5],                           // division
                (String) r[6],                           // area
                (String) r[7],                           // machine
                (String) r[8],                           // riskFreq
                (String) r[9],                           // riskProb
                (String) r[10],                          // riskSev
                (String) r[11],                          // riskTotal
                (String) r[12],                          // comment
                (String) r[13],                          // countermeasure
                (String) r[14],                          // checkInfo
                splitImages((String) r[15]),             // imageNames
                (LocalDateTime) r[16],                    // createdAt
                (String) r[17],                          // pic
                (LocalDateTime) r[18],                    // dueDate
                splitImages((String) r[19]),             // at_imageNames
                (String) r[20],                          // at_comment
                (LocalDateTime) r[21],                    // at_date
                (String) r[22],                          // at_pic
                (String) r[23],                          // at_status
                (String) r[24],                          // hse_judge
                splitImages((String) r[25]),             // hse_imageNames
                (String) r[26],                          // hse_comment
                (LocalDateTime) r[27],                    // hse_date
                (String) r[28]                           // load_status
        );
    }


    private List<String> splitImages(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .toList();
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public String  replaceImage(
            Long reportId,
            String oldImageName,
            MultipartFile newImage
    ) throws IOException {

        PatrolReport report = repo.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        // 1️⃣ Tách danh sách ảnh hiện tại
        String imageNames = report.getImageNames();
        if (imageNames == null || imageNames.isBlank()) {
            throw new RuntimeException("No images to update");
        }

        List<String> images = new ArrayList<>(
                Arrays.stream(imageNames.split(","))
                        .map(String::trim)
                        .toList()
        );

        // 2️⃣ Check ảnh cũ có tồn tại không
        int index = images.indexOf(oldImageName);
        if (index == -1) {
            throw new RuntimeException("Old image not found in report");
        }

        // 3️⃣ Lưu ảnh mới (dùng lại logic saveImageFiles)
        String newImageName = saveSingleImage(newImage);

        // 4️⃣ Replace đúng vị trí
        images.set(index, newImageName);

        // 5️⃣ Update DB
        report.setImageNames(String.join(",", images));
        repo.save(report);

        // 6️⃣ Xóa file ảnh cũ (SAU khi DB save thành công)
        deleteImageFile(oldImageName);
        return newImageName;
    }

    private String saveSingleImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("New image is empty");
        }

        if (!Files.exists(imageFolderPath)) {
            Files.createDirectories(imageFolderPath);
        }

        String ext = ".jpg";
        if (file.getOriginalFilename() != null &&
                file.getOriginalFilename().contains(".")) {
            ext = file.getOriginalFilename()
                    .substring(file.getOriginalFilename().lastIndexOf("."));
        }

        String fileName = System.currentTimeMillis()
                + "_" + UUID.randomUUID() + ext;

        Path savePath = imageFolderPath.resolve(fileName);
        file.transferTo(savePath.toFile());

        return fileName;
    }

    private void deleteImageFile(String imageName) {
        try {
            Path path = imageFolderPath.resolve(imageName);
            Files.deleteIfExists(path);
        } catch (Exception e) {
            // không throw để tránh rollback DB
            System.err.println("Failed to delete old image: " + imageName);
        }
    }


    @Transactional
    public void updateAtInfo(
            Long reportId,
            AtUpdateDTO dto,
            List<MultipartFile> images
    ) throws IOException {

        PatrolReport report = repo.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        // ===== SAVE IMAGE FILES =====
        List<String> newImages = new ArrayList<>();

        if (images != null && !images.isEmpty()) {
            if (!Files.exists(imageFolderPath)) {
                Files.createDirectories(imageFolderPath);
            }

            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) continue;

                String ext = ".jpg";
                if (file.getOriginalFilename() != null &&
                        file.getOriginalFilename().contains(".")) {
                    ext = file.getOriginalFilename()
                            .substring(file.getOriginalFilename().lastIndexOf("."));
                }

                String fileName = System.currentTimeMillis()
                        + "_" + UUID.randomUUID() + ext;

                Path savePath = imageFolderPath.resolve(fileName);
                file.transferTo(savePath.toFile());

                newImages.add(fileName);
            }
        }

        String imageNames = String.join(",", newImages);
        String atStatus = "Done";
        int updated = repo.updateAtInfo(
                reportId,
                imageNames,
                dto.getAtComment(),
                LocalDateTime.now(),
                dto.getAtPic(),
                atStatus
        );

        if (updated == 0) {
            throw new RuntimeException("Update AT failed, id=" + reportId);
        }
    }

}
