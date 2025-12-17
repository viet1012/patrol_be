package com.example.patrol_be.service;
import java.util.List;

import com.example.patrol_be.dto.HSEPatrolGroupMasterDTO;
import com.example.patrol_be.repository.HSEPatrolGroupMasterRepo;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
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
                        (String) r[3]
                ))
                .toList();
    }
}
