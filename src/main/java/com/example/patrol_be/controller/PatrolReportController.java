package com.example.patrol_be.controller;

import com.example.patrol_be.dto.*;
import com.example.patrol_be.service.PatrolCommentService;
import com.example.patrol_be.service.PatrolMachineAnalysisService;
import com.example.patrol_be.service.PatrolPivotService;
import com.example.patrol_be.service.PatrolReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patrol_report")
@RequiredArgsConstructor
@Slf4j
public class PatrolReportController {

	private final PatrolMachineAnalysisService patrolMachineAiService;
	private final ObjectMapper objectMapper = new ObjectMapper();
	@Autowired
	private PatrolReportService service;
	@Autowired
	private PatrolPivotService pivotService;
	@Autowired
	private  PatrolCommentService patrolCommentService;

	private String extractJson(String text) {

		if (text == null || text.isBlank()) {
			return "{}";
		}

		text = text.trim();

		text = text.replace("```json", "");
		text = text.replace("```", "");
		text = text.trim();

		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');

		if (start >= 0 && end > start) {
			return text.substring(start, end + 1);
		}

		return "{}";
	}

	@GetMapping(
			value = "/analyze-machine",
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<JsonNode> analyzeMachine(
			@RequestParam String machine,
			@RequestParam(required = false) String area
	) throws Exception {

		String aiText = patrolMachineAiService.analyzeMachine(machine, area);

		JsonNode json = safeReadAiJson(aiText);

		return ResponseEntity.ok(json);
	}

	@PostMapping("/translate-ai-summary")
	public ResponseEntity<?> translateAiSummary(
			@RequestBody TranslateAiSummaryRequest req
	) {
		String text = req.getText();

		if (text == null || text.isBlank()) {
			return ResponseEntity.ok(Map.of("text", ""));
		}

		String jp = patrolCommentService.getTranslateDefault(text);

		return ResponseEntity.ok(Map.of("text", jp));
	}

	private JsonNode safeReadAiJson(String aiText) {
		try {
			String cleanJson = extractJson(aiText);
			return objectMapper.readTree(cleanJson);
		} catch (Exception e) {
			ObjectNode node = objectMapper.createObjectNode();
			node.put("found", false);
			node.put("message", "Invalid AI JSON");
			node.put("summaryVi", "");
			node.put("summaryJp", "");
			node.put("raw", aiText == null ? "" : aiText);
			return node;
		}
	}

	@GetMapping("/machine-history")
	public List<MachineIssueHistoryDTO> getMachineHistory(
			@RequestParam(required = false) String fac,
			@RequestParam(required = false) String division,
			@RequestParam(required = false) String area,
			@RequestParam(required = false) String machine,
			@RequestParam(required = false, defaultValue = "6") Integer months
	) {
		return patrolMachineAiService.getMachineIssueHistory(
				fac,
				division,
				area,
				machine,
				months
		);
	}

	@GetMapping("/filter")
	public ResponseEntity<?> filter(
			@RequestParam(required = false) String plant,
			@RequestParam(required = false) String grp,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String division,
			@RequestParam(required = false) String area,
			@RequestParam(required = false) String machine,
			@RequestParam(required = false) String afStatus,
			@RequestParam(required = false) String pic,
			@RequestParam(required = false) String patrolUser,
			@RequestParam(required = false) String qrKey,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromD,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toD
	) {

		return ResponseEntity.ok(
				service.search(
						plant,
						division,
						area,
						machine,
						type,
						afStatus,
						grp,
						pic,
						patrolUser,
						qrKey,
						fromD,
						toD
				)
		);

	}

	@GetMapping("/pivot")
	public PatrolRiskPivotResponseDTO pivot(
			@RequestParam String plant,
			@RequestParam String type,
			@RequestParam(name = "at_status") List<String> atStatus
	) {
		return pivotService.getPivot(plant, atStatus, type);
	}


	@PutMapping("{id}/replace_image")
	public ResponseEntity<?> replaceImage(
			@PathVariable Long id,
			@RequestParam String oldImage,
			@RequestParam MultipartFile newImage
	) throws IOException {
		String newImageName = service.replaceImage(id, oldImage, newImage);
		return ResponseEntity.ok(
				Map.of("newImage", newImageName)
		);
	}

	// ================== DELETE IMAGE ==================
	@DeleteMapping("/{id}/delete_image")
	public ResponseEntity<?> deleteImage(
			@PathVariable Long id,
			@RequestParam String image
	) {
		service.deleteImage(id, image);
		return ResponseEntity.ok("Image deleted successfully");
	}


	// ================== ADD IMAGE ==================
	@PostMapping("/{id}/add_image")
	public ResponseEntity<?> addImage(
			@PathVariable Long id,
			@RequestParam MultipartFile image
	) throws IOException {
		String newImageName = service.addImage(id, image);
		return ResponseEntity.ok(
				Map.of("newImage", newImageName)
		);
	}

	// ================== REPLACE IMAGE ==================
	@PutMapping("/{id}/update_at")
	public ResponseEntity<?> updateAt(
			@PathVariable Long id,
			@RequestParam("data") String dto,
			@RequestParam(value = "images", required = false)
			List<MultipartFile> images
	) throws IOException {
		AtUpdateDTO atUpdateDTO = new ObjectMapper().readValue(dto, AtUpdateDTO.class);

		service.updateAtInfo(id, atUpdateDTO, images);
		return ResponseEntity.ok("AT updated successfully");
	}


	@PostMapping(value = "/{id}/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> updateReport(
			@PathVariable Long id,
			@RequestParam("data") String dto,
			@RequestParam(value = "images", required = false) List<MultipartFile> images
	) throws IOException {
		PatrolEditDTO atUpdateDTO = new ObjectMapper().readValue(dto, PatrolEditDTO.class);

		service.updateReport(id, atUpdateDTO, images);
		return ResponseEntity.ok().build();
	}


	@PutMapping("/{id}/hse_recheck")
	public ResponseEntity<?> hseRecheck(
			@PathVariable Long id,
			@RequestParam("data") String dto,
			@RequestParam(value = "images", required = false)
			List<MultipartFile> images
	) throws IOException {
		HseUpdateDTO atUpdateDTO = new ObjectMapper().readValue(dto, HseUpdateDTO.class);

		service.updateHseInfo(id, atUpdateDTO, images);
		return ResponseEntity.ok("AT updated successfully");
	}

	@GetMapping("/risk_summary")
	public ResponseEntity<List<RiskSummaryDTO>> getRiskSummary(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromD,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toD,
			@RequestParam String fac,
			@RequestParam(defaultValue = "Patrol") String type
	) {
		return ResponseEntity.ok(
				service.getRiskSummary(fromD, toD, fac, type)
		);
	}

	// GET /api/patrol_report/summary/division?fromD=2026-01-02&toD=2026-01-28&fac=Fac_2&type=Patrol
	@GetMapping("/summary/division")
	public ResponseEntity<List<DivisionSummaryDTO>> byDivision(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromD,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toD,
			@RequestParam String fac,
			@RequestParam String type
	) {
		return ResponseEntity.ok(service.summaryByDivision(fromD, toD, fac, type));
	}

	// GET /api/patrol_report/pic-summary?fromD=2025-12-01&toD=2026-01-28&fac=Fac_2&type=Patrol&lvls=IV&lvls=V
//    @GetMapping("/pic-summary")
//    public List<PicSummaryDTO> picSummary(
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromD,
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toD,
//            @RequestParam String fac,
//            @RequestParam String type,
//            @RequestParam List<String> lvls
//    ) {
//        return service.getPicSummary(fromD, toD, fac, type, lvls);
//    }

	// /api/patrol/summary?from=2025-12-05&to=2026-02-26&plant=Fac_2&type=Patrol
	@GetMapping("/summary")
	public PatrolSummaryResponseDTO summary(
			@RequestParam("from") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam("to") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam("plant") String plant,
			@RequestParam("type") String type
	) {
		return service.getSummary(from, to, plant, type);
	}

}
