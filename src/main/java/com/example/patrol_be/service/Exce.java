
package com.example.patrol_be.service;

import com.example.patrol_be.dto.DuplicateQrException;
import com.example.patrol_be.dto.InvalidQrException;
import com.example.patrol_be.dto.QrCheckResponse;
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

	@Transactional(readOnly = true)
	public QrCheckResponse checkQr(String qrKey) {
		String normalizedQr = normalize(qrKey);

		if (isBlank(normalizedQr)) {
			return QrCheckResponse.builder()
					.qrKey(null)
					.valid(false)
					.available(false)
					.duplicate(false)
					.message("QR code is required.")
					.build();
		}

		if (!normalizedQr.matches(QR_PATTERN)) {
			return QrCheckResponse.builder()
					.qrKey(normalizedQr)
					.valid(false)
					.available(false)
					.duplicate(false)
					.message(
							"QR code must contain only numbers "
									+ "and have a maximum of 5 digits."
					)
					.build();
		}

		boolean duplicate = reportRepo.existsOpenByQrKey(
				normalizedQr,
				STATUS_CLOSED
		);

		if (duplicate) {
			return QrCheckResponse.builder()
					.qrKey(normalizedQr)
					.valid(true)
					.available(false)
					.duplicate(true)
					.message(
							"QR code "
									+ normalizedQr
									+ " already exists and has not been closed."
					)
					.build();
		}

		return QrCheckResponse.builder()
				.qrKey(normalizedQr)
				.valid(true)
				.available(true)
				.duplicate(false)
				.message("QR code is available.")
				.build();
	}

	private boolean isPatrol(
			ReportRequest request
	) {
		return request != null
				&& "Patrol".equalsIgnoreCase(
				normalize(
						request.getType()
				)
		);
	}
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

		// Chỉ Patrol mới normalize + validate required fields
		if ("Patrol".equalsIgnoreCase(request.getType())) {
			normalizeRequest(request);
			validateRequiredFields(request);
		}

		String qrKey;

		if (isPatrol(request)) {

			// Chỉ Patrol:
			// - validate QR 1-5 số
			// - check duplicate
			qrKey =
					normalizeAndValidateQr(
							request.getQr_key()
					);

		} else {

			// Non-Patrol:
			// chỉ normalize và lưu
			// KHÔNG check duplicate
			qrKey =
					normalize(
							request.getQr_key()
					);
		}

		request.setQr_key(
				qrKey
		);

		createImageDirectory();

		List<String> savedImageNames = new ArrayList<>();

		try {
			int stt = sttService.nextByFacAndType(
					request.getPlant(),
					request.getType()
			);

			request.setStt(stt);

//			savedImageNames = saveImageFiles(images);
			savedImageNames = saveImageFiles(
					images,
					request.getType()
			);

			request.setImageFileNames(savedImageNames);

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

		} catch (DataIntegrityViolationException exception) {

			deleteSavedImages(
					savedImageNames,
					request.getType()
			);

			throw exception;

		} catch (IOException exception) {

			deleteSavedImages(
					savedImageNames,
					request.getType()
			);

			throw exception;

		} catch (RuntimeException exception) {

			deleteSavedImages(
					savedImageNames,
					request.getType()
			);

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

		/*
		 * Nội dung tiếng Việt
		 */
		report.setComment(
				normalize(request.getComment())
		);

		report.setCountermeasure(
				normalize(request.getCountermeasure())
		);

		/*
		 * Nội dung tiếng Nhật
		 */
		report.setComment_jp(
				normalize(request.getComment_jp())
		);

		report.setCountermeasure_jp(
				normalize(request.getCountermeasure_jp())
		);

		report.setCheckInfo(
				request.getCheck()
		);

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
	// TRANSLATION
	// ============================================================

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

	// ============================================================
	// SAVE IMAGES
	// ============================================================
	private void createImageDirectory() throws IOException {
		Files.createDirectories(IMAGE_FOLDER_PATH);
	}

	private List<String> saveImageFiles(
			MultipartFile[] images,
			String reportType
	) throws IOException {

		List<String> savedNames = new ArrayList<>();

		if (images == null || images.length == 0) {
			return savedNames;
		}

		Path targetFolder = resolveImageFolder(reportType);

		Files.createDirectories(targetFolder);

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

				Path savePath = targetFolder
						.resolve(fileName)
						.normalize();

				if (!savePath.startsWith(targetFolder)) {
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

				if ("Patrol".equalsIgnoreCase(reportType)) {

					savedNames.add(fileName);

				} else {

					String safeFolderName =
							normalize(reportType)
									.replaceAll(
											"[^a-zA-Z0-9_-]",
											"_"
									);

					savedNames.add(
							safeFolderName + "/" + fileName
					);
				}

				savedNames.add(fileName);
			}

			return savedNames;

		} catch (IOException | RuntimeException exception) {

			deleteSavedImages(
					savedNames,
					reportType
			);

			throw exception;
		}
	}

	//	private List<String> saveImageFiles(
//			MultipartFile[] images
//	) throws IOException {
//		List<String> savedNames = new ArrayList<>();
//
//		if (images == null || images.length == 0) {
//			return savedNames;
//		}
//
//		try {
//			for (MultipartFile image : images) {
//				if (image == null || image.isEmpty()) {
//					continue;
//				}
//
//				String extension = resolveImageExtension(
//						image.getOriginalFilename()
//				);
//
//				String fileName =
//						System.currentTimeMillis()
//								+ "_"
//								+ UUID.randomUUID()
//								+ extension;
//
//				Path savePath = IMAGE_FOLDER_PATH
//						.resolve(fileName)
//						.normalize();
//
//				/*
//				 * Chặn path traversal.
//				 */
//				if (!savePath.startsWith(IMAGE_FOLDER_PATH)) {
//					throw new IOException(
//							"Invalid image storage path."
//					);
//				}
//
//				try (var inputStream = image.getInputStream()) {
//					Files.copy(
//							inputStream,
//							savePath,
//							StandardCopyOption.REPLACE_EXISTING
//					);
//				}
//
//				savedNames.add(fileName);
//			}
//
//			return savedNames;
//
//		} catch (IOException | RuntimeException exception) {
//			/*
//			 * Nếu ảnh thứ 3 lỗi thì xóa ảnh 1 và 2 đã lưu.
//			 */
//			deleteSavedImages(savedNames);
//			throw exception;
//		}
//	}
	private Path resolveImageFolder(
			String reportType
	) {

		String normalizedType =
				normalize(reportType);

		// Patrol vẫn giữ folder cũ
		if ("Patrol".equalsIgnoreCase(normalizedType)) {
			return IMAGE_FOLDER_PATH;
		}

		if (isBlank(normalizedType)) {
			return IMAGE_FOLDER_PATH
					.resolve("Unknown")
					.normalize();
		}

		// Chỉ cho ký tự an toàn trong tên folder
		String safeFolderName =
				normalizedType.replaceAll(
						"[^a-zA-Z0-9_-]",
						"_"
				);

		Path folder =
				IMAGE_FOLDER_PATH
						.resolve(safeFolderName)
						.normalize();

		// chống ../ path traversal
		if (!folder.startsWith(IMAGE_FOLDER_PATH)) {
			throw new IllegalArgumentException(
					"Invalid report type folder."
			);
		}

		return folder;
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

	//	private void deleteSavedImages(
//			List<String> fileNames
//	) {
//		if (fileNames == null || fileNames.isEmpty()) {
//			return;
//		}
//
//		for (String fileName : fileNames) {
//			if (isBlank(fileName)) {
//				continue;
//			}
//
//			try {
//				Path filePath = IMAGE_FOLDER_PATH
//						.resolve(fileName)
//						.normalize();
//
//				if (!filePath.startsWith(IMAGE_FOLDER_PATH)) {
//					continue;
//				}
//
//				Files.deleteIfExists(filePath);
//
//			} catch (IOException exception) {
//				log.warn(
//						"Cannot delete rollback image: {}",
//						fileName,
//						exception
//				);
//			}
//		}
//	}
	private void deleteSavedImages(
			List<String> fileNames,
			String reportType
	) {

		if (fileNames == null || fileNames.isEmpty()) {
			return;
		}

		Path targetFolder =
				resolveImageFolder(reportType);

		for (String fileName : fileNames) {

			if (isBlank(fileName)) {
				continue;
			}

			try {

				Path filePath =
						targetFolder
								.resolve(fileName)
								.normalize();

				if (!filePath.startsWith(targetFolder)) {
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
		request.setType(
				normalize(request.getType())
		);

		request.setGroup(
				normalize(request.getGroup())
		);

		request.setPlant(
				normalize(request.getPlant())
		);

		request.setDivision(
				normalize(request.getDivision())
		);

		request.setArea(
				normalize(request.getArea())
		);

		request.setMachine(
				normalize(request.getMachine())
		);

		request.setRiskFreq(
				normalize(request.getRiskFreq())
		);

		request.setRiskProb(
				normalize(request.getRiskProb())
		);

		request.setRiskSev(
				normalize(request.getRiskSev())
		);

		request.setRiskTotal(
				normalize(request.getRiskTotal())
		);

		request.setComment(
				normalize(request.getComment())
		);

		request.setCountermeasure(
				normalize(request.getCountermeasure())
		);

		request.setComment_jp(
				normalize(request.getComment_jp())
		);

		request.setCountermeasure_jp(
				normalize(request.getCountermeasure_jp())
		);

		request.setCheck(
				normalize(request.getCheck())
		);

		request.setUserCreate(
				normalize(request.getUserCreate())
		);

		request.setQr_key(
				normalize(request.getQr_key())
		);

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

		boolean hasVietnameseComment =
				!isBlank(request.getComment());

		boolean hasJapaneseComment =
				!isBlank(request.getComment_jp());

		if (!hasVietnameseComment && !hasJapaneseComment) {
			throw new IllegalArgumentException(
					"Comment is required."
			);
		}
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