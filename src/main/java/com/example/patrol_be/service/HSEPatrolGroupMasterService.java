package com.example.patrol_be.service;

import com.example.patrol_be.dto.HSEPatrolGroupMasterDTO;
import com.example.patrol_be.dto.MachineInfoDTO;
import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HSEPatrolGroupMasterService {
	private final HSEPatrolGroupMasterRepo repo;

	public List<HSEPatrolGroupMasterDTO> getAll() {
		return repo.findAllMachines().stream()
				.map(r -> new HSEPatrolGroupMasterDTO(
						(String) r[0],
						(String) r[1],
						(String) r[2],
						(String) r[3],
						(String) r[4]
				))
				.toList();
	}

	public List<MachineInfoDTO> findByMacId(String macId) {
		return repo.findMachineInfoByMacId(macId).stream()
				.map(r -> new MachineInfoDTO(
						toStr(r[0]),
						toStr(r[1]),
						toStr(r[2]),
						toStr(r[3]),
						toStr(r[4])
				))
				.toList();
	}

	private String toStr(Object value) {
		return value == null ? "" : value.toString().trim();
	}
}
