package com.example.patrol_be.service;

import com.example.patrol_be.dto.*;
import com.example.patrol_be.dto.PatrolSummaryResponseDTO;
import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.PatrolReportRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatrolReportService {

    private final PatrolReportRepo repo;
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);

    private final PatrolCommentService patrolCommentService;

    public List<PatrolReportDTO> search(
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
    ) {
//        System.out.println("Group: " + grp);

        return repo.search(
                        normalize(plant),
                        normalize(division),
                        normalize(area),
                        normalize(machine),
                        normalize(type),

                        normalize(grp),          // ✅ ĐÚNG VỊ TRÍ
                        normalize(afStatus),     // ✅ ĐÚNG VỊ TRÍ

                        normalizeKeepEmpty(pic),
                        normalize(patrolUser),
                        normalize(qrKey),
                        from,
                        to
                ).stream()
                .map(this::mapToDTO)
                .toList();
    }


    private PatrolReportDTO mapToDTO(Object[] r) {
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
                (String) r[28],                           // load_status
                (String) r[29]          ,               // patrol_user
                (String) r[30]                        // qr_key

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

    // ✅ giữ empty string (để filter pic rỗng)
    private String normalizeKeepEmpty(String v) {
        return v == null ? null : v.trim();
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
    public void deleteImage(Long reportId, String imageName) {

        PatrolReport report = repo.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        String imageNames = report.getImageNames();
        if (imageNames == null || imageNames.isBlank()) {
            throw new RuntimeException("No images to delete");
        }

        List<String> images = new ArrayList<>(
                Arrays.stream(imageNames.split(","))
                        .map(String::trim)
                        .toList()
        );

        boolean removed = images.remove(imageName);
        if (!removed) {
            throw new RuntimeException("Image not found in report");
        }

        // Update DB
        report.setImageNames(String.join(",", images));
        repo.save(report);

        // Delete file
        deleteImageFile(imageName);
    }

    @Transactional
    public String addImage(Long reportId, MultipartFile image) throws IOException {

        PatrolReport report = repo.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        // Save image file
        String newImageName = saveSingleImage(image);

        String imageNames = report.getImageNames();

        if (imageNames == null || imageNames.isBlank()) {
            report.setImageNames(newImageName);
        } else {
            report.setImageNames(imageNames + "," + newImageName);
        }

        repo.save(report);

        return newImageName;
    }

    @Transactional
    public void updateAtInfo(
            Long reportId,
            AtUpdateDTO DTO,
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

        String finalComment = DTO.getAtComment();
        try {
            if (finalComment != null && !finalComment.isBlank()) {

                String translated = patrolCommentService.getTranslateDefault(finalComment);

                if (translated != null) {
                    finalComment += "\n" + translated;
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // ? in l?i luôn cho d? debug
        }


        String imageNames = String.join(",", newImages);
        String atStatus = "Done";
        int updated = repo.updateAtInfo(
                reportId,
                imageNames,
                finalComment,
                LocalDateTime.now(),
                DTO.getAtPic(),
                atStatus

        );

        if (updated == 0) {
            throw new RuntimeException("Update AT failed, id=" + reportId);
        }
    }

    @Transactional
    public void updateReport(
            Long reportId,
            PatrolEditDTO DTO,
            List<MultipartFile> newImages
    ) throws IOException {

        PatrolReport report = repo.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        // ===== 1️⃣ UPDATE COMMENT & COUNTERMEASURE =====
        if (DTO.getComment() != null) {
            report.setComment(DTO.getComment().trim());
        }

        if (DTO.getCountermeasure() != null) {
            report.setCountermeasure(DTO.getCountermeasure().trim());
        }

        // ===== ✅ 1.5️⃣ UPDATE META FIELDS =====
        if (DTO.getGrp() != null) {
            report.setGrp(DTO.getGrp().trim());
        }
        if (DTO.getPlant() != null) {
            report.setPlant(DTO.getPlant().trim());
        }
        if (DTO.getDivision() != null) {
            report.setDivision(DTO.getDivision().trim());
        }
        if (DTO.getArea() != null) {
            report.setArea(DTO.getArea().trim());
        }
        if (DTO.getMachine() != null) {
            // nếu muốn convert "<Null>" => null
            String m = DTO.getMachine().trim();
            report.setMachine(m.equalsIgnoreCase("<Null>") || m.isEmpty() ? null : m);
        }
        if (DTO.getPic() != null) {
            report.setPic(DTO.getPic().trim());
        }

        if (DTO.getRiskFreq() != null) {
            report.setRiskFreq(DTO.getRiskFreq().trim());
        }

        if (DTO.getRiskProb() != null) {
            report.setRiskProb(DTO.getRiskProb().trim());
        }

        if (DTO.getRiskSev() != null) {
            report.setRiskSev(DTO.getRiskSev().trim());
        }

        if (DTO.getRiskTotal() != null) {
            report.setRiskTotal(DTO.getRiskTotal().trim());
        }

        if (DTO.getAtComment() != null) {
            report.setAt_comment(DTO.getAtComment().trim());
        }

        if (DTO.getAtStatus() != null) {
            report.setAt_status(DTO.getAtStatus().trim());
        }

        if (DTO.getAtUser() != null) {
            report.setAt_user(DTO.getAtUser().trim());
        }

        report.setEdit_date(LocalDateTime.now());
        report.setEdit_user(DTO.getEditUser());

        // ===== 2️⃣ HANDLE IMAGE LIST =====
        List<String> images = new ArrayList<>();

        if (report.getImageNames() != null && !report.getImageNames().isBlank()) {
            images.addAll(
                    Arrays.stream(report.getImageNames().split(","))
                            .map(String::trim)
                            .toList()
            );
        }

        // ===== 3️⃣ DELETE IMAGES =====
        if (DTO.getDeleteImages() != null) {
            for (String img : DTO.getDeleteImages()) {
                if (images.remove(img)) {
                    deleteImageFile(img);
                }
            }
        }

        // ===== 4️⃣ ADD NEW IMAGES =====
        if (newImages != null && !newImages.isEmpty()) {
            for (MultipartFile file : newImages) {
                if (file == null || file.isEmpty()) continue;

                String newImageName = saveSingleImage(file);
                images.add(newImageName);
            }
        }

        // ===== 5️⃣ SAVE IMAGE NAMES =====
        report.setImageNames(images.isEmpty() ? null : String.join(",", images));

        repo.save(report);
    }


    @Transactional
    public void updateHseInfo(
            Long reportId,
            HseUpdateDTO DTO,
            List<MultipartFile> images
    ) throws IOException {

        // (optional) nếu bạn chỉ muốn check tồn tại
        repo.findById(reportId)
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
                String original = file.getOriginalFilename();
                if (original != null && original.contains(".")) {
                    ext = original.substring(original.lastIndexOf("."));
                }

                String fileName = System.currentTimeMillis()
                        + "_" + UUID.randomUUID() + ext;

                Path savePath = imageFolderPath.resolve(fileName);
                file.transferTo(savePath.toFile());

                newImages.add(fileName);
            }
        }

        // ===== COMMENT (kèm translate) =====
        String finalComment = DTO.getHseComment();
        try {
            if (finalComment != null && !finalComment.isBlank()) {
                String translated = patrolCommentService.getTranslateDefault(finalComment);
                if (translated != null && !translated.isBlank()) {
                    finalComment = finalComment + "\n" + translated;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String imageNames = String.join(",", newImages);

        int updated = repo.updateHseInfo(
                reportId,
                imageNames,
                finalComment,
                LocalDateTime.now(),
                DTO.getHseUser(),
                DTO.getHseJudge(),
                DTO.getAtStatus()

        );

        if (updated == 0) {
            throw new RuntimeException("Update HSE failed, id=" + reportId);
        }
    }


    public List<RiskSummaryDTO> getRiskSummary(
            LocalDate from,
            LocalDate to,
            String fac,
            String type
    ) {
        return repo.summaryRiskRaw(from, to, fac, type)
                .stream()
                .map(r -> new RiskSummaryDTO(
                        (String) r[0],   // grp
                        (String) r[1],   // division
                        ((Number) r[2]).intValue(), // minus
                        ((Number) r[3]).intValue(), // i
                        ((Number) r[4]).intValue(), // ii
                        ((Number) r[5]).intValue(), // iii
                        ((Number) r[6]).intValue(), // iv
                        ((Number) r[7]).intValue()  // v
                ))
                .toList();
    }


    public List<DivisionSummaryDTO> summaryByDivision(
            LocalDate fromD, LocalDate toD, String fac, String type
    ) {
        List<Object[]> rows = repo.summaryByDivisionRaw(fromD, toD, fac, type);
        List<DivisionSummaryDTO> out = new ArrayList<>(rows.size() + 2);

        for (Object[] r : rows) {
            int i = 0;
            String division = (String) r[i++];

            out.add(new DivisionSummaryDTO(
                    division,
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++])
            ));
        }

        // ✅ SUM row
        DivisionSummaryDTO sum = sumRow(out);
        sum.setDivision("SUM");
        out.add(sum);

        // ✅ % row (chỉ TTL) - lưu theo basis points (x10000)
        DivisionSummaryDTO pct = pctRowFrom(sum);
        pct.setDivision("%");
        out.add(pct);

        return out;
    }

    private DivisionSummaryDTO sumRow(List<DivisionSummaryDTO> rows) {
        DivisionSummaryDTO s = new DivisionSummaryDTO();
        s.setDivision("SUM");

        for (DivisionSummaryDTO d : rows) {
            s.setAllTtl(s.getAllTtl() + d.getAllTtl());
            s.setAllI(s.getAllI() + d.getAllI());
            s.setAllII(s.getAllII() + d.getAllII());
            s.setAllIII(s.getAllIII() + d.getAllIII());
            s.setAllIV(s.getAllIV() + d.getAllIV());
            s.setAllV(s.getAllV() + d.getAllV());

            s.setProDoneTtl(s.getProDoneTtl() + d.getProDoneTtl());
            s.setProDoneI(s.getProDoneI() + d.getProDoneI());
            s.setProDoneII(s.getProDoneII() + d.getProDoneII());
            s.setProDoneIII(s.getProDoneIII() + d.getProDoneIII());
            s.setProDoneIV(s.getProDoneIV() + d.getProDoneIV());
            s.setProDoneV(s.getProDoneV() + d.getProDoneV());

            s.setHseDoneTtl(s.getHseDoneTtl() + d.getHseDoneTtl());
            s.setHseDoneI(s.getHseDoneI() + d.getHseDoneI());
            s.setHseDoneII(s.getHseDoneII() + d.getHseDoneII());
            s.setHseDoneIII(s.getHseDoneIII() + d.getHseDoneIII());
            s.setHseDoneIV(s.getHseDoneIV() + d.getHseDoneIV());
            s.setHseDoneV(s.getHseDoneV() + d.getHseDoneV());

            s.setRemainTtl(s.getRemainTtl() + d.getRemainTtl());
            s.setRemainI(s.getRemainI() + d.getRemainI());
            s.setRemainII(s.getRemainII() + d.getRemainII());
            s.setRemainIII(s.getRemainIII() + d.getRemainIII());
            s.setRemainIV(s.getRemainIV() + d.getRemainIV());
            s.setRemainV(s.getRemainV() + d.getRemainV());
        }
        return s;
    }

    /**
     * % row: chỉ set các *_Ttl.
     * Lưu theo basis points (x10000): 12.34% => 1234
     * - All_Ttl luôn 100% => 10000
     * - ProDoneTtl = ProDoneTtl / AllTtl
     * - HseDoneTtl = HseDoneTtl / AllTtl
     * - RemainTtl  = RemainTtl  / AllTtl
     */
//    private DivisionSummaryDTO pctRowFrom(DivisionSummaryDTO sum) {
//        DivisionSummaryDTO p = new DivisionSummaryDTO();
//        p.setDivision("%");
//
//        long all = sum.getAllTtl();
//        p.setAllTtl(10000L);
//
//        p.setProDoneTtl(ratioBp(sum.getProDoneTtl(), all));
//        p.setHseDoneTtl(ratioBp(sum.getHseDoneTtl(), all));
//        p.setRemainTtl(ratioBp(sum.getRemainTtl(), all));
//
//        // các cột khác giữ 0 (vì bạn chỉ muốn % TTL)
//        return p;
//    }
    private DivisionSummaryDTO pctRowFrom(DivisionSummaryDTO sum) {
        DivisionSummaryDTO p = new DivisionSummaryDTO();
        p.setDivision("%");

        long all = (long) sum.getAllTtl();
        long pro = (long) sum.getProDoneTtl();

        p.setAllTtl(100.0);

        // % trên ALL
        p.setProDoneTtl(ratioPercent((long) sum.getProDoneTtl(), all));
        p.setRemainTtl(ratioPercent((long) sum.getRemainTtl(), all));

        // % trên PRO (HSE ⊆ PRO)
        p.setHseDoneTtl(ratioPercent((long) sum.getHseDoneTtl(), pro));

        return p;
    }

    /**
     * Trả về % dạng 72.22
     */
    private double ratioPercent(long part, long total) {
        if (total <= 0) return 0d;
        return Math.round((part * 10000.0) / total) / 100.0;
    }



    public List<DivisionSummaryDTO> summaryByDivision1(LocalDate fromD, LocalDate toD, String fac, String type) {
        List<Object[]> rows = repo.summaryByDivisionRaw(fromD, toD, fac, type);
        List<DivisionSummaryDTO> out = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
            int i = 0;
            String division = (String) r[i++];

            out.add(new DivisionSummaryDTO(
                    division,
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]),
                    toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++]), toLong(r[i++])
            ));
        }
        return out;
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }


    public List<PicSummaryDTO> getPicSummary(LocalDate fromD, LocalDate toD, String fac, String type, List<String> lvls) {
        return repo.fetchPicSummaryRaw(fromD, toD, fac, type, lvls)
                .stream()
                .map(this::mapRow)
                .toList();
    }
    public PatrolSummaryResponseDTO getSummary(LocalDate fromD, LocalDate toD, String plant, String type) {
        var rows = repo.summaryByFacAndPic(fromD, toD, plant, type);

        // group by fac
        Map<String, List<PatrolSummaryRowView>> byFac = rows.stream()
                .collect(Collectors.groupingBy(PatrolSummaryRowView::getFac, LinkedHashMap::new, Collectors.toList()));

        List<PatrolFacSummaryDTO> facs = new ArrayList<>();

        for (var e : byFac.entrySet()) {
            String fac = e.getKey();
            List<PatrolSummaryRowView> list = e.getValue();

            PatrolSummaryRowView totalRow = list.stream()
                    .filter(r -> "TOTAL".equalsIgnoreCase(nullSafe(r.getPic())))
                    .findFirst()
                    .orElse(null);

            List<PatrolPicRowDTO> detailRows = list.stream()
                    .filter(r -> !"TOTAL".equalsIgnoreCase(nullSafe(r.getPic())))
                    .map(this::toPicRow)
                    .toList();

            PatrolPicRowDTO totalDTO = totalRow == null ? null : toPicRow(totalRow);

            // rates
            double finishedTtl = totalRow == null ? 0 : n(totalRow.getFinishedTtl());
            double remainTtl = totalRow == null ? 0 : n(totalRow.getRemainTtl());
            double denomAfter = finishedTtl + remainTtl;

            Double finishedRate = denomAfter == 0 ? null : round2(finishedTtl / denomAfter);
            Double remainRate = denomAfter == 0 ? null : round2(remainTtl / denomAfter);

            double reAll = totalRow == null ? 0 : n(totalRow.getRecheckAllTtl());
            double reOk = totalRow == null ? 0 : n(totalRow.getRecheckOkTtl());
            double reNg = totalRow == null ? 0 : n(totalRow.getRecheckNgTtl());
            Double okRate = reAll == 0 ? null : round2(reOk / reAll);
            Double ngRate = reAll == 0 ? null : round2(reNg / reAll);

            facs.add(PatrolFacSummaryDTO.builder()
                    .fac(fac)
                    .rows(detailRows)
                    .total(totalDTO)
                    .finishedRate(finishedRate)
                    .remainRate(remainRate)
                    .okRate(okRate)
                    .ngRate(ngRate)
                    .build());
        }

        return PatrolSummaryResponseDTO.builder()
                .fromD(fromD)
                .toD(toD)
                .plant(plant)
                .type(type)
                .facs(facs)
                .build();
    }

    private PatrolPicRowDTO toPicRow(PatrolSummaryRowView r) {
        return PatrolPicRowDTO.builder()
                .pic(r.getPic())
                .before(risk(r.getBeforeTtl(), r.getBeforeI(), r.getBeforeII(), r.getBeforeIII(), r.getBeforeIV(), r.getBeforeV()))
                .finished(risk(r.getFinishedTtl(), r.getFinishedI(), r.getFinishedII(), r.getFinishedIII(), r.getFinishedIV(), r.getFinishedV()))
                .remain(risk(r.getRemainTtl(), r.getRemainI(), r.getRemainII(), r.getRemainIII(), r.getRemainIV(), r.getRemainV()))
                .recheckAllTotal(n(r.getRecheckAllTtl()))
                .recheckOk(risk(r.getRecheckOkTtl(), r.getRecheckOkI(), r.getRecheckOkII(), r.getRecheckOkIII(), r.getRecheckOkIV(), r.getRecheckOkV()))
                .recheckNg(risk(r.getRecheckNgTtl(), r.getRecheckNgI(), r.getRecheckNgII(), r.getRecheckNgIII(), r.getRecheckNgIV(), r.getRecheckNgV()))
                .build();
    }

    private RiskBreakdownDTO risk(Long ttl, Long i, Long ii, Long iii, Long iv, Long v) {
        return RiskBreakdownDTO.builder()
                .total(n(ttl))
                .i(n(i))
                .ii(n(ii))
                .iii(n(iii))
                .iv(n(iv))
                .v(n(v))
                .build();
    }

    private long n(Long x) { return x == null ? 0L : x; }
    private String nullSafe(String s) { return s == null ? "" : s; }

    private Double round2(double x) {
        return Math.round(x * 100.0) / 100.0; // 0.74 => 74% trên UI
    }

    private PicSummaryDTO mapRow(Object[] r) {
        int i = 0;
        return PicSummaryDTO.builder()
                .pic((String) r[i++])

                .allTtl(toInt(r[i++]))
                .allNyPct(toInt(r[i++]))
                .allOk(toInt(r[i++]))
                .allNg(toInt(r[i++]))
                .allNy(toInt(r[i++]))

                .facATtl(toInt(r[i++]))
                .facANyPct(toInt(r[i++]))
                .facAOk(toInt(r[i++]))
                .facANg(toInt(r[i++]))
                .facANy(toInt(r[i++]))

                .facBTtl(toInt(r[i++]))
                .facBNyPct(toInt(r[i++]))
                .facBOk(toInt(r[i++]))
                .facBNg(toInt(r[i++]))
                .facBNy(toInt(r[i++]))

                .facCTtl(toInt(r[i++]))
                .facCNyPct(toInt(r[i++]))
                .facCOk(toInt(r[i++]))
                .facCNg(toInt(r[i++]))
                .facCNy(toInt(r[i++]))

                .outsideTtl(toInt(r[i++]))
                .outsideNyPct(toInt(r[i++]))
                .outsideOk(toInt(r[i++]))
                .outsideNg(toInt(r[i++]))
                .outsideNy(toInt(r[i++]))
                .build();
    }

    private Integer toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }
}
