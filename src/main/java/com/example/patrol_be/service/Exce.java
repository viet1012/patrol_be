package com.example.patrol_be.service;

import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
import com.example.patrol_be.repository.PatrolReportRepo;

import com.example.patrol_be.dto.ReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;


@Service
@RequiredArgsConstructor
public class Exce {

    private final PatrolReportRepo reportRepo;
    private final SttService sttService;
    private final PatrolCommentService patrolCommentService;
    private final HSEPatrolGroupMasterRepo hsePatrolGroupMasterRepo;

    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
    private static final String EXCEL_FILE_NAME = "reports.xlsx";
    private static final String IMAGE_FOLDER_NAME = "uploaded_images";

    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);


    private Path getExcelFilePathByPlant(String plant) {
        String safePlant = plant == null ? "default" : plant.replaceAll("\\W+", "_");
        String fileName = "Safety_Patrol_" + safePlant + ".xlsx";
        return BASE_DIR.resolve(fileName);
    }

    // ===========================
    // MAIN FUNCTION
    // ===========================
    private static String norm(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').trim();
    }
    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
    public String findPicSmart(String plant, String grp, String area, String macId) {
        plant = norm(plant);
        grp   = norm(grp);
        area  = norm(area);
        macId = norm(macId);

        String pic = null;

        if (!blank(area) && !blank(macId)) {
            pic = hsePatrolGroupMasterRepo.findPicByPlantGrpAreaMac(plant, grp, area, macId);
            if (!blank(pic)) return norm(pic);
        }

        if (!blank(area)) {
            pic = hsePatrolGroupMasterRepo.findPicByPlantGrpArea(plant, grp, area);
            if (!blank(pic)) return norm(pic);
        }

        pic = hsePatrolGroupMasterRepo.findPicByPlantGrp(plant, grp);
        return blank(pic) ? null : norm(pic);
    }

    public synchronized void appendToExcel(ReportRequest req, MultipartFile[] images) throws IOException {

        if (!Files.exists(imageFolderPath)) Files.createDirectories(imageFolderPath);

        // 1) Lấy STT theo group
        int stt = sttService.nextByFacAndType(
                req.getPlant(),
                req.getType()
        );
        req.setStt(stt);

        // 2) Lưu ảnh
        List<String> savedImageNames = saveImageFiles(images);
        req.setImageFileNames(savedImageNames);

        // 3) Dịch comment / countermeasure
        try {
            if (req.getComment() != null && !req.getComment().isBlank()) {
                String translated = patrolCommentService.getTranslateDefault(req.getComment());
                if (translated != null) {
                    req.setComment(req.getComment() + "\n" + translated);
                }
            }
            if (req.getCountermeasure() != null && !req.getCountermeasure().isBlank()) {
                String translated = patrolCommentService.getTranslateDefault(req.getCountermeasure());
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
        rpt.setType(req.getType());

//        String grp = req.getGroup().replace(" ", "").trim();
        rpt.setGrp(req.getGroup());
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
//        rpt.setPic(hsePatrolGroupMasterRepo.findPIC(rpt.getPlant(), rpt.getMachine()));
        String pic = findPicSmart(
                req.getPlant(),
                req.getDivision(),
                req.getArea(),
                req.getMachine()
        );

        System.out.println("🔎 [PIC RESOLVE RESULT]");
        System.out.println("   Plant   = " + req.getPlant());
        System.out.println("   Division   = " + req.getDivision());
        System.out.println("   Area    = " + req.getArea());
        System.out.println("   Machine = " + req.getMachine());
        System.out.println("👉 PIC = " + pic);

        rpt.setPic(pic);


        if ("IV".equals(req.getRiskTotal()) || "V".equals(req.getRiskTotal())){
            rpt.setDueDate((LocalDate.now().plusDays(14)));
        }
        else {
            rpt.setDueDate((LocalDate.now().plusDays(28)));
        }
        rpt.setPatrol_user(req.getUserCreate());
        rpt.setAt_status("Wait");
        rpt.setQr_key(req.getQr_key());
        rpt.setQr_scan_sts(req.getQr_scan_sts());

        reportRepo.save(rpt);
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



    private String s(String v) {
        return v == null ? "" : v;
    }


}
