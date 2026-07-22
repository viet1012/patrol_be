//package com.example.patrol_be.service;
//
//import com.example.patrol_be.dto.DuplicateQrException;
//import com.example.patrol_be.dto.InvalidQrException;
//import com.example.patrol_be.model.PatrolReport;
//import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
//import com.example.patrol_be.repository.PatrolReportRepo;
//
//import com.example.patrol_be.dto.ReportRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.*;
//import java.nio.file.*;
//import java.time.LocalDate;
//import java.util.*;
//
//
//@Service
//@RequiredArgsConstructor
//public class Exce {
//
//    private final PatrolReportRepo reportRepo;
//    private final SttService sttService;
//    private final PatrolCommentService patrolCommentService;
//    private final HSEPatrolGroupMasterRepo hsePatrolGroupMasterRepo;
//
//    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"));
//    private static final String EXCEL_FILE_NAME = "reports.xlsx";
//    private static final String IMAGE_FOLDER_NAME = "uploaded_images";
//
//    private final Path excelFilePath = BASE_DIR.resolve(EXCEL_FILE_NAME);
//    private final Path imageFolderPath = BASE_DIR.resolve(IMAGE_FOLDER_NAME);
//
//    public boolean canUseQr(String qrKey) {
//        qrKey = norm(qrKey);
//
//        if (blank(qrKey)) return true;
//
//        // Chỉ cho tối đa 5 số
//        if (!qrKey.matches("^\\d{1,5}$")) {
//            throw new InvalidQrException(
//                    "QR code must contain only numbers and have a maximum of 5 digits."
//            );
//        }
//
//        long openCount = reportRepo.countOpenByQrKey(qrKey);
//
//        return openCount == 0;
//    }
//
//    // ===========================
//    // MAIN FUNCTION
//    // ===========================
//    private static String norm(String s) {
//        if (s == null) return null;
//        return s.replace('\u00A0', ' ').trim();
//    }
//
//    private static boolean blank(String s) {
//        return s == null || s.trim().isEmpty();
//    }
//
//    public String findPicSmart(String plant, String grp, String area, String macId) {
//        plant = norm(plant);
//        grp   = norm(grp);
//        area  = norm(area);
//        macId = norm(macId);
//
//        System.out.println("FIND PIC INPUT => plant=[" + plant +
//                "], grp=[" + grp +
//                "], area=[" + area +
//                "], macId=[" + macId + "]");
//
//        String pic;
//
//        // 1. Check d? Plant + Group + Area + MacId
//        if (!blank(plant) && !blank(grp) && !blank(area) && !blank(macId)) {
//            pic = hsePatrolGroupMasterRepo.findPicByPlantGrpAreaMac(
//                    plant, grp, area, macId
//            );
//
//            System.out.println("PIC BY AREA + MAC => [" + pic + "]");
//
//            if (!blank(pic)) {
//                return norm(pic);
//            }
//        }
//
//        // 2. Fallback Plant + Group + MacId
//        if (!blank(plant) && !blank(grp) && !blank(macId)) {
//            pic = hsePatrolGroupMasterRepo.findPicByPlantGrpMac(
//                    plant, grp, macId
//            );
//
//            System.out.println("PIC BY MAC ONLY => [" + pic + "]");
//
//            if (!blank(pic)) {
//                return norm(pic);
//            }
//        }
//
//        // 3. Fallback Plant + Group + Area
//        if (!blank(plant) && !blank(grp) && !blank(area)) {
//            pic = hsePatrolGroupMasterRepo.findPicByPlantGrpArea(
//                    plant, grp, area
//            );
//
//            System.out.println("PIC BY AREA => [" + pic + "]");
//
//            if (!blank(pic)) {
//                return norm(pic);
//            }
//        }
//
//        // 4. Fallback Plant + Group
//        if (!blank(plant) && !blank(grp)) {
//            pic = hsePatrolGroupMasterRepo.findPicByPlantGrp(plant, grp);
//
//            System.out.println("PIC BY GROUP => [" + pic + "]");
//
//            return blank(pic) ? null : norm(pic);
//        }
//
//        return null;
//    }
//
//    public synchronized void appendToExcel(ReportRequest req, MultipartFile[] images) throws IOException {
//        String qrKey = norm(req.getQr_key());
//
//        if (!blank(qrKey)) {
//            if (!qrKey.matches("^\\d{1,5}$")) {
//                throw new RuntimeException("Invalid QR code. QR must be numbers only and max 5 digits.");
//            }
//
//            boolean canUseQr = canUseQr(qrKey);
//
//            if (!canUseQr) {
//                throw new DuplicateQrException(qrKey);
//            }
//
//            req.setQr_key(qrKey);
//        }
//
//        if (!Files.exists(imageFolderPath)) Files.createDirectories(imageFolderPath);
//
//        // 1) Lấy STT theo group
//        int stt = sttService.nextByFacAndType(
//                req.getPlant(),
//                req.getType()
//        );
//        req.setStt(stt);
//
//        // 2) Lưu ảnh
//        List<String> savedImageNames = saveImageFiles(images);
//        req.setImageFileNames(savedImageNames);
//
//        // 3) Dịch comment / countermeasure
//        try {
//            if (req.getComment() != null && !req.getComment().isBlank()) {
//                String translated = patrolCommentService.getTranslateDefault(req.getComment());
//                if (translated != null) {
//                    req.setComment(req.getComment() + "\n" + translated);
//                }
//            }
//            if (req.getCountermeasure() != null && !req.getCountermeasure().isBlank()) {
//                String translated = patrolCommentService.getTranslateDefault(req.getCountermeasure());
//                if (translated != null) {
//                    req.setCountermeasure(req.getCountermeasure() + "\n" + translated);
//                }
//            }
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        // 4) Lưu vào database
//
//
//        PatrolReport rpt = new PatrolReport();
//        rpt.setStt(stt);
//        rpt.setType(req.getType());
//
//        rpt.setGrp(req.getGroup());
//        rpt.setPlant(req.getPlant());
//        rpt.setDivision(req.getDivision());
//        rpt.setArea(req.getArea());
//        rpt.setMachine(req.getMachine());
//
//        rpt.setRiskFreq(req.getRiskFreq());
//        rpt.setRiskProb(req.getRiskProb());
//        rpt.setRiskSev(req.getRiskSev());
//        rpt.setRiskTotal(req.getRiskTotal());
//
//        rpt.setComment(req.getComment());
//        rpt.setCountermeasure(req.getCountermeasure());
//        rpt.setCheckInfo(req.getCheck());
//        rpt.setImageNames(String.join(",", savedImageNames));
//
//        String pic = findPicSmart(
//                req.getPlant(),
//                req.getDivision(),
//                req.getArea(),
//                req.getMachine()
//        );
//
//        System.out.println("🔎 [PIC RESOLVE RESULT]");
//        System.out.println("   Plant   = " + req.getPlant());
//        System.out.println("   Division   = " + req.getDivision());
//        System.out.println("   Area    = " + req.getArea());
//        System.out.println("   Machine = " + req.getMachine());
//        System.out.println("👉 PIC = " + pic);
//
//        rpt.setPic(pic);
//
//
//        if ("IV".equals(req.getRiskTotal()) || "V".equals(req.getRiskTotal())){
//            rpt.setDueDate((LocalDate.now().plusDays(14)));
//        }
//        else {
//            rpt.setDueDate((LocalDate.now().plusDays(28)));
//        }
//        rpt.setPatrol_user(req.getUserCreate());
//        rpt.setAt_status("Doing");
//        rpt.setQr_key(qrKey);
//        rpt.setQr_scan_sts(req.getQr_scan_sts());
//
//        reportRepo.save(rpt);
//    }
//
//    // ===========================
//    // SAVE IMAGES
//    // ===========================
//    private List<String> saveImageFiles(MultipartFile[] images) throws IOException {
//        List<String> list = new ArrayList<>();
//        if (images == null) return list;
//
//        for (MultipartFile f : images) {
//            if (f.isEmpty()) continue;
//
//            String ext = ".jpg";
//            if (f.getOriginalFilename() != null && f.getOriginalFilename().contains(".")) {
//                ext = f.getOriginalFilename().substring(f.getOriginalFilename().lastIndexOf("."));
//            }
//
//            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
//            Path savePath = imageFolderPath.resolve(fileName);
//
//            f.transferTo(savePath.toFile());
//            list.add(fileName);
//        }
//        return list;
//    }
//
//
//
//    private String s(String v) {
//        return v == null ? "" : v;
//    }
//
//
//}

package com.example.patrol_be.service;

import com.example.patrol_be.dto.DuplicateQrException;
import com.example.patrol_be.dto.InvalidQrException;
import com.example.patrol_be.dto.ReportRequest;
import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
import com.example.patrol_be.repository.PatrolReportRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Exce {

	private static final String STATUS_DOING = "Doing";
	private static final String STATUS_CLOSED = "Closed";

	private static final int HIGH_RISK_DUE_DAYS = 14;
	private static final int NORMAL_RISK_DUE_DAYS = 28;

	private static final String QR_PATTERN = "^\\d{1,5}$";

	private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
			".jpg",
			".jpeg",
			".png",
			".webp"
	);

	private static final Path BASE_DIR =
			Paths.get(System.getProperty("user.dir"))
					.toAbsolutePath()
					.normalize();

	private static final Path IMAGE_FOLDER_PATH =
			BASE_DIR.resolve("uploaded_images")
					.normalize();

	private final PatrolReportRepo reportRepo;
	private final SttService sttService;
	private final PatrolCommentService patrolCommentService;
	private final HSEPatrolGroupMasterRepo hsePatrolGroupMasterRepo;

	// ============================================================
	// QR
	// ============================================================

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value
				.replace('\u00A0', ' ')
				.trim();

		return normalized.isEmpty()
				? null
				: normalized;
	}

	private static boolean isBlank(String value) {
		return value == null
				|| value.trim().isEmpty();
	}

	public boolean canUseQr(String qrKey) {
		String normalizedQr = normalize(qrKey);

		if (isBlank(normalizedQr)) {
			return true;
		}

		validateQr(normalizedQr);

		return !reportRepo.existsOpenByQrKey(
				normalizedQr,
				STATUS_CLOSED
		);
	}

	// ============================================================
	// CREATE REPORT
	// ============================================================

	private void validateQr(String qrKey) {
		if (isBlank(qrKey)) {
			return;
		}

		if (!qrKey.matches(QR_PATTERN)) {
			throw new InvalidQrException(
					"QR code must contain only numbers and have a maximum of 5 digits."
			);
		}
	}

	// ============================================================
	// BUILD ENTITY
	// ============================================================

	private String normalizeAndValidateQr(String qrKey) {
		String normalizedQr = normalize(qrKey);

		if (isBlank(normalizedQr)) {
			return null;
		}

		validateQr(normalizedQr);

		if (reportRepo.existsOpenByQrKey(
				normalizedQr,
				STATUS_CLOSED
		)) {
			throw new DuplicateQrException(normalizedQr);
		}

		return normalizedQr;
	}

	// ============================================================
	// DUE DATE
	// ============================================================

	@Transactional
	public void appendToExcel(
			ReportRequest request,
			MultipartFile[] images
	) throws IOException {

		if (request == null) {
			throw new IllegalArgumentException(
					"Report request must not be null."
			);
		}

		normalizeRequest(request);
		validateRequiredFields(request);

		String qrKey = normalizeAndValidateQr(
				request.getQr_key()
		);

		request.setQr_key(qrKey);

		createImageDirectory();

		List<String> savedImageNames = new ArrayList<>();

		try {
			int stt = sttService.nextByFacAndType(
					request.getPlant(),
					request.getType()
			);

			request.setStt(stt);

			savedImageNames = saveImageFiles(images);
			request.setImageFileNames(savedImageNames);

			String finalComment = translateAndAppend(
					request.getComment(),
					"comment"
			);

			String finalCountermeasure = translateAndAppend(
					request.getCountermeasure(),
					"countermeasure"
			);

			request.setComment(finalComment);
			request.setCountermeasure(finalCountermeasure);

			String pic = resolvePic(
					request.getPlant(),
					request.getDivision(),

					request.getArea(),
					request.getMachine()
			);

			PatrolReport report = buildReport(
					request,
					stt,
					qrKey,
					savedImageNames,
					pic
			);

			reportRepo.saveAndFlush(report);

			log.info(
					"Patrol report created: stt={}, plant={}, type={}, qrKey={}, pic={}, imageCount={}",
					stt,
					request.getPlant(),
					request.getType(),
					qrKey,
					pic,
					savedImageNames.size()
			);

		} catch (DataIntegrityViolationException exception) {
			/*
			 * Xóa ảnh nếu database từ chối insert,
			 * ví dụ unique index phát hiện QR trùng.
			 */
			deleteSavedImages(savedImageNames);

			if (!isBlank(qrKey)) {
				log.warn(
						"Duplicate or invalid database data for QR: {}",
						qrKey,
						exception
				);

				throw new DuplicateQrException(qrKey);
			}

			throw exception;

		} catch (IOException exception) {
			deleteSavedImages(savedImageNames);
			throw exception;

		} catch (RuntimeException exception) {
			deleteSavedImages(savedImageNames);
			throw exception;
		}
	}

	private PatrolReport buildReport(
			ReportRequest request,
			int stt,
			String qrKey,
			List<String> savedImageNames,
			String pic
	) {
		PatrolReport report = new PatrolReport();

		report.setStt(stt);
		report.setType(request.getType());

		report.setGrp(request.getGroup());
		report.setPlant(request.getPlant());
		report.setDivision(request.getDivision());
		report.setArea(request.getArea());
		report.setMachine(request.getMachine());

		report.setRiskFreq(request.getRiskFreq());
		report.setRiskProb(request.getRiskProb());
		report.setRiskSev(request.getRiskSev());
		report.setRiskTotal(request.getRiskTotal());

		report.setComment(request.getComment());
		report.setCountermeasure(request.getCountermeasure());
		report.setCheckInfo(request.getCheck());

		report.setImageNames(
				String.join(",", savedImageNames)
		);

		report.setPic(pic);

		report.setDueDate(
				calculateDueDate(request.getRiskTotal())
		);

		report.setPatrol_user(
				request.getUserCreate()
		);

		report.setAt_status(STATUS_DOING);
		report.setQr_key(qrKey);
		report.setQr_scan_sts(
				request.getQr_scan_sts()
		);

		return report;
	}

	private LocalDate calculateDueDate(String riskTotal) {
		String normalizedRisk = normalize(riskTotal);

		if ("IV".equalsIgnoreCase(normalizedRisk)
				|| "V".equalsIgnoreCase(normalizedRisk)) {
			return LocalDate.now().plusDays(
					HIGH_RISK_DUE_DAYS
			);
		}

		return LocalDate.now().plusDays(
				NORMAL_RISK_DUE_DAYS
		);
	}

	// ============================================================
	// TRANSLATION
	// ============================================================

	// ============================================================
	// FIND PIC
	// ============================================================
	public String resolvePic(
			String plant,
			String grp,
			String area,
			String macId
	) {
		String normalizedPlant = normalize(plant);
		String normalizedGroup = normalize(grp);
		String normalizedArea = normalize(area);
		String normalizedMacId = normalize(macId);

		if (isBlank(normalizedPlant) || isBlank(normalizedGroup)) {
			log.info(
					"Cannot resolve PIC because plant or group is empty: plant={}, group={}",
					normalizedPlant,
					normalizedGroup
			);
			return null;
		}

		String pic = hsePatrolGroupMasterRepo.resolvePic(
				normalizedPlant,
				normalizedGroup,
				normalizedArea,
				normalizedMacId
		);

		pic = normalize(pic);

		log.info(
				"Resolve PIC result: plant={}, group={}, area={}, machine={}, pic={}",
				normalizedPlant,
				normalizedGroup,
				normalizedArea,
				normalizedMacId,
				pic
		);

		return isBlank(pic) ? null : pic;
	}

	// ============================================================
	// SAVE IMAGES
	// ============================================================

	public String findPicSmart1(
			String plant,
			String grp,
			String area,
			String macId
	) {
		String normalizedPlant = normalize(plant);
		String normalizedGroup = normalize(grp);
		String normalizedArea = normalize(area);
		String normalizedMacId = normalize(macId);

		if (isBlank(normalizedPlant)
				|| isBlank(normalizedGroup)) {
			log.info(
					"Cannot resolve PIC because plant or group is empty: plant={}, group={}",
					normalizedPlant,
					normalizedGroup
			);

			return null;
		}

		log.info(
				"Resolve PIC: plant={}, group={}, area={}, machine={}",
				normalizedPlant,
				normalizedGroup,
				normalizedArea,
				normalizedMacId
		);

		String pic;

		// 1. Plant + Group + Area + Machine
		if (!isBlank(normalizedArea)
				&& !isBlank(normalizedMacId)) {

			pic = hsePatrolGroupMasterRepo
					.findPicByPlantGrpAreaMac(
							normalizedPlant,
							normalizedGroup,
							normalizedArea,
							normalizedMacId
					);

			if (!isBlank(pic)) {
				return normalize(pic);
			}
		}

		// 2. Plant + Group + Machine
		if (!isBlank(normalizedMacId)) {
			pic = hsePatrolGroupMasterRepo
					.findPicByPlantGrpMac(
							normalizedPlant,
							normalizedGroup,
							normalizedMacId
					);

			if (!isBlank(pic)) {
				return normalize(pic);
			}
		}

		// 3. Plant + Group + Area
		if (!isBlank(normalizedArea)) {
			pic = hsePatrolGroupMasterRepo
					.findPicByPlantGrpArea(
							normalizedPlant,
							normalizedGroup,
							normalizedArea
					);

			if (!isBlank(pic)) {
				return normalize(pic);
			}
		}

		// 4. Plant + Group
		pic = hsePatrolGroupMasterRepo.findPicByPlantGrp(
				normalizedPlant,
				normalizedGroup
		);

		return isBlank(pic)
				? null
				: normalize(pic);
	}

	private String translateAndAppend(
			String originalText,
			String fieldName
	) {
		String normalizedText = normalize(originalText);

		if (isBlank(normalizedText)) {
			return normalizedText;
		}

		try {
			String translated =
					patrolCommentService.getTranslateDefault(
							normalizedText
					);

			translated = normalize(translated);

			if (isBlank(translated)) {
				return normalizedText;
			}

			/*
			 * Tránh nối lại nếu dịch vụ trả đúng nguyên văn.
			 */
			if (normalizedText.equalsIgnoreCase(translated)) {
				return normalizedText;
			}

			return normalizedText
					+ System.lineSeparator()
					+ translated;

		} catch (Exception exception) {
			/*
			 * Dịch lỗi vẫn cho phép lưu report.
			 */
			log.warn(
					"Cannot translate {}. Original text will be saved.",
					fieldName,
					exception
			);

			return normalizedText;
		}
	}

	private void createImageDirectory() throws IOException {
		Files.createDirectories(IMAGE_FOLDER_PATH);
	}

	private List<String> saveImageFiles(
			MultipartFile[] images
	) throws IOException {
		List<String> savedNames = new ArrayList<>();

		if (images == null || images.length == 0) {
			return savedNames;
		}

		try {
			for (MultipartFile image : images) {
				if (image == null || image.isEmpty()) {
					continue;
				}

				String extension = resolveImageExtension(
						image.getOriginalFilename()
				);

				String fileName =
						System.currentTimeMillis()
								+ "_"
								+ UUID.randomUUID()
								+ extension;

				Path savePath = IMAGE_FOLDER_PATH
						.resolve(fileName)
						.normalize();

				/*
				 * Chặn path traversal.
				 */
				if (!savePath.startsWith(IMAGE_FOLDER_PATH)) {
					throw new IOException(
							"Invalid image storage path."
					);
				}

				try (var inputStream = image.getInputStream()) {
					Files.copy(
							inputStream,
							savePath,
							StandardCopyOption.REPLACE_EXISTING
					);
				}

				savedNames.add(fileName);
			}

			return savedNames;

		} catch (IOException | RuntimeException exception) {
			/*
			 * Nếu ảnh thứ 3 lỗi thì xóa ảnh 1 và 2 đã lưu.
			 */
			deleteSavedImages(savedNames);
			throw exception;
		}
	}

	// ============================================================
	// REQUEST VALIDATION
	// ============================================================

	private String resolveImageExtension(
			String originalFileName
	) {
		if (isBlank(originalFileName)) {
			return ".jpg";
		}

		String cleanFileName = Paths
				.get(originalFileName)
				.getFileName()
				.toString();

		int dotIndex = cleanFileName.lastIndexOf('.');

		if (dotIndex < 0
				|| dotIndex == cleanFileName.length() - 1) {
			return ".jpg";
		}

		String extension = cleanFileName
				.substring(dotIndex)
				.toLowerCase(Locale.ROOT);

		if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
			log.warn(
					"Unsupported image extension: {}. Use .jpg instead.",
					extension
			);

			return ".jpg";
		}

		return extension;
	}

	private void deleteSavedImages(
			List<String> fileNames
	) {
		if (fileNames == null || fileNames.isEmpty()) {
			return;
		}

		for (String fileName : fileNames) {
			if (isBlank(fileName)) {
				continue;
			}

			try {
				Path filePath = IMAGE_FOLDER_PATH
						.resolve(fileName)
						.normalize();

				if (!filePath.startsWith(IMAGE_FOLDER_PATH)) {
					continue;
				}

				Files.deleteIfExists(filePath);

			} catch (IOException exception) {
				log.warn(
						"Cannot delete rollback image: {}",
						fileName,
						exception
				);
			}
		}
	}

	private void normalizeRequest(
			ReportRequest request
	) {
		request.setType(normalize(request.getType()));
		request.setGroup(normalize(request.getGroup()));
		request.setPlant(normalize(request.getPlant()));
		request.setDivision(normalize(request.getDivision()));
		request.setArea(normalize(request.getArea()));
		request.setMachine(normalize(request.getMachine()));

		request.setRiskFreq(normalize(request.getRiskFreq()));
		request.setRiskProb(normalize(request.getRiskProb()));
		request.setRiskSev(normalize(request.getRiskSev()));
		request.setRiskTotal(normalize(request.getRiskTotal()));

		request.setComment(normalize(request.getComment()));
		request.setCountermeasure(
				normalize(request.getCountermeasure())
		);
		request.setCheck(normalize(request.getCheck()));
		request.setUserCreate(
				normalize(request.getUserCreate())
		);
		request.setQr_key(normalize(request.getQr_key()));
		request.setQr_scan_sts(
				normalize(request.getQr_scan_sts())
		);
	}

	// ============================================================
	// STRING HELPERS
	// ============================================================

	private void validateRequiredFields(
			ReportRequest request
	) {
		requireText(
				request.getPlant(),
				"Plant is required."
		);

		requireText(
				request.getType(),
				"Report type is required."
		);

		requireText(
				request.getArea(),
				"Area is required."
		);

		requireText(
				request.getMachine(),
				"Machine is required."
		);

		requireText(
				request.getComment(),
				"Comment is required."
		);
	}

	private void requireText(
			String value,
			String message
	) {
		if (isBlank(value)) {
			throw new IllegalArgumentException(message);
		}
	}
}