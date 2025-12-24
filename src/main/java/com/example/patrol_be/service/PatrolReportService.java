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
                ((Number) r[0]).intValue(),
                ((Number) r[1]).intValue(),     // stt
                (String) r[2],                  // grp
                (String) r[3],                  // plant
                (String) r[4],                  // division
                (String) r[5],                  // area
                (String) r[6],                  // machine
                (String) r[7],                  // riskFreq
                (String) r[8],                  // riskProb
                (String) r[9],                  // riskSev
                (String) r[10],                  // riskTotal
                (String) r[11],                 // comment
                (String) r[12],                 // countermeasure
                (String) r[13],                 // checkInfo
                (LocalDateTime) r[14],          // checkInfo
                splitImages((String) r[15])     // imageNames
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
