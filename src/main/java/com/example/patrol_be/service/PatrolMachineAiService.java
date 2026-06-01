package com.example.patrol_be.service;

import com.example.patrol_be.dto.MachineIssueHistoryDTO;
import com.example.patrol_be.model.HSEPatrolGroupMaster;
import com.example.patrol_be.model.PatrolReport;
import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
import com.example.patrol_be.repository.PatrolReportRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatrolMachineAiService {

	private final PatrolReportRepo repo;
	private final HSEPatrolGroupMasterRepo masterRepo;

	private final PatrolCommentService patrolCommentService;
	private final ObjectMapper objectMapper = new ObjectMapper()
			.findAndRegisterModules();

	public String analyzeMachine(String machine) {
		String machineKey = blankToNull(machine);

		if (machineKey == null) {
			return """
					{
					  "found": false,
					  "message": "Machine is required",
					  "summaryVi": "",
					  "summaryJp": ""
					}
					""";
		}

		try {
			List<MachineIssueHistoryDTO> history =
					getMachineIssueHistory(null, null, null, machineKey, 6);

			if (history.isEmpty()) {
				return """
						{
						  "found": false,
						  "machine": "%s",
						  "message": "No patrol history found",
						  "summaryVi": "",
						  "summaryJp": ""
						}
						""".formatted(machineKey);
			}

			String aiInputJson = buildAiInputJson(machineKey, history);

			System.out.println("AI INPUT JSON = " + aiInputJson);

			String aiResult = patrolCommentService.analyzeMachineIssues(aiInputJson);

			System.out.println("AI RESULT = " + aiResult);

			return aiResult;

		} catch (Exception e) {
			e.printStackTrace();

			return """
					{
					  "found": false,
					  "machine": "%s",
					  "message": "AI analyze failed: %s",
					  "summaryVi": "",
					  "summaryJp": ""
					}
					""".formatted(machineKey, safeJsonText(e.getMessage()));
		}
	}


	private String buildAiInputJson(
			String machine,
			List<MachineIssueHistoryDTO> history
	) throws Exception {

		List<String> areas = history.stream()
				.map(MachineIssueHistoryDTO::getArea)
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.toList();

		List<String> relatedMachines = history.stream()
				.map(MachineIssueHistoryDTO::getMachine)
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.toList();

		List<String> comments = history.stream()
				.flatMap(h -> h.getComments().stream())
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.limit(40)
				.toList();

		int totalCases = history.stream()
				.map(MachineIssueHistoryDTO::getTotalCases)
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();

		String fac = history.stream()
				.map(MachineIssueHistoryDTO::getFac)
				.filter(s -> !isBlank(s))
				.findFirst()
				.orElse("");

		String division = history.stream()
				.map(MachineIssueHistoryDTO::getDivision)
				.filter(s -> !isBlank(s))
				.findFirst()
				.orElse("");

		ObjectNode node = objectMapper.createObjectNode();

		node.put("machine", machine);
		node.put("fac", fac);
		node.put("division", division);
		node.put("totalCases", totalCases);

		node.set("areas", objectMapper.valueToTree(areas));
		node.set("relatedMachines", objectMapper.valueToTree(relatedMachines));
		node.set("comments", objectMapper.valueToTree(comments));

		return objectMapper.writeValueAsString(node);
	}


	public String analyzeMachine1(String machine) {
		String machineKey = blankToNull(machine);

		if (machineKey == null) {
			return """
					{
					  "found": false,
					  "message": "Machine is required",
					  "summaryVi": "",
					  "summaryJp": ""
					}
					""";
		}

		try {
			List<MachineIssueHistoryDTO> history = getMachineIssueHistory(
					null,
					null,
					null,
					machineKey,
					6
			);

			if (history.isEmpty()) {
				return """
						{
						  "found": false,
						  "machine": "%s",
						  "message": "No patrol history found for this machine",
						  "summaryVi": "",
						  "summaryJp": ""
						}
						""".formatted(machineKey);
			}

			String aiInputJson = buildAiInputJson1(machineKey, history);

			System.out.println("AI INPUT JSON = " + aiInputJson);

			String aiResult = patrolCommentService.analyzeMachineIssues(aiInputJson);

			System.out.println("AI RESULT = " + aiResult);

			return aiResult;

		} catch (Exception e) {
			e.printStackTrace();

			return """
					{
					  "found": false,
					  "machine": "%s",
					  "message": "AI analyze failed: %s",
					  "summaryVi": "",
					  "summaryJp": ""
					}
					""".formatted(machineKey, safeJsonText(e.getMessage()));
		}
	}

	private String buildAiInputJson1(
			String machine,
			List<MachineIssueHistoryDTO> history
	) throws Exception {

		List<String> areas = history.stream()
				.map(MachineIssueHistoryDTO::getArea)
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.toList();

		List<String> comments = history.stream()
				.flatMap(h -> h.getComments().stream())
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.limit(30)
				.toList();

		int totalCases = history.stream()
				.map(MachineIssueHistoryDTO::getTotalCases)
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.sum();

		String fac = history.stream()
				.map(MachineIssueHistoryDTO::getFac)
				.filter(s -> !isBlank(s))
				.findFirst()
				.orElse("");

		String division = history.stream()
				.map(MachineIssueHistoryDTO::getDivision)
				.filter(s -> !isBlank(s))
				.findFirst()
				.orElse("");

		ObjectNode node = objectMapper.createObjectNode();

		node.put("machine", machine);
		node.put("fac", fac);
		node.put("division", division);
		node.put("totalCases", totalCases);
		node.set("areas", objectMapper.valueToTree(areas));
		node.set("comments", objectMapper.valueToTree(comments));

		return objectMapper.writeValueAsString(node);
	}

	public List<MachineIssueHistoryDTO> getMachineIssueHistory(
			String fac,
			String division,
			String area,
			String machine,
			Integer months
	) {
		String machineKey = blankToNull(machine);

		LocalDateTime fromDate = months == null || months <= 0
				? null
				: LocalDateTime.now().minusMonths(months);

		List<PatrolReport> reports;

		if (machineKey != null) {
			List<String> targetMachines = findMachinesBySameCate(machineKey);

			if (targetMachines.isEmpty()) {
				targetMachines = List.of(machineKey);
			}

			reports = repo.findAiIssueHistoryByMachines(
					targetMachines.stream()
							.map(String::toLowerCase)
							.toList(),
					fromDate
			);
		} else {
			reports = repo.findAiIssueHistory(
					blankToNull(fac),
					blankToNull(division),
					blankToNull(area),
					null,
					fromDate
			);
		}

		return reports.stream()
				.filter(r -> !isBlank(r.getMachine()))
				.filter(r -> !isBlank(r.getComment()))
				.sorted(
						Comparator.comparing(
								PatrolReport::getCreatedAt,
								Comparator.nullsLast(Comparator.reverseOrder())
						)
				)
				.collect(Collectors.groupingBy(
						r -> key(
								r.getPlant(),
								r.getDivision(),
								r.getArea(),
								r.getMachine()
						)
				))
				.values()
				.stream()
				.map(this::toMachineIssueHistory)
				.toList();
	}

	private List<String> findMachinesBySameCate(String machine) {
		var masterOpt = masterRepo.findFirstByMacIdIgnoreCase(machine);

		if (masterOpt.isEmpty()) {
			return List.of();
		}

		String cate = blankToNull(masterOpt.get().getCate());

		if (cate == null) {
			return List.of();
		}

		return masterRepo.findByCateIgnoreCase(cate)
				.stream()
				.map(HSEPatrolGroupMaster::getMacId)
				.filter(s -> !isBlank(s))
				.map(String::trim)
				.distinct()
				.toList();
	}

	private MachineIssueHistoryDTO toMachineIssueHistory(List<PatrolReport> list) {
		PatrolReport first = list.get(0);

		return MachineIssueHistoryDTO.builder()
				.fac(first.getPlant())
				.division(first.getDivision())
				.area(first.getArea())
				.machine(first.getMachine())
				.comments(
						list.stream()
								.map(PatrolReport::getComment)
								.filter(s -> !isBlank(s))
								.map(String::trim)
								.distinct()
								.limit(10)
								.toList()
				)
				.totalCases(list.size())
				.build();
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private String key(String fac, String division, String area, String machine) {
		return String.join("|",
				Objects.toString(fac, "").trim(),
				Objects.toString(division, "").trim(),
				Objects.toString(area, "").trim(),
				Objects.toString(machine, "").trim()
		);
	}

	private String safeJsonText(String value) {
		if (value == null) return "";
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}
}