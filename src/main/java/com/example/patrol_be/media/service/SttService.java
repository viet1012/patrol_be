package com.example.patrol_be.service;

import com.example.patrol_be.model.PatrolGroupStt;
import com.example.patrol_be.repository.PatrolGroupSttRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SttService {

    private final PatrolGroupSttRepo repo;
    private final SimpMessagingTemplate messaging;

    // ===============================
    // TĂNG STT THEO FAC + TYPE
    // ===============================
    @Transactional
    public int nextByFacAndType(String fac, String type) {

        String facClean = fac.trim();
        String typeClean = type.trim();

        PatrolGroupStt stt = repo
                .findForUpdateByFacAndType(facClean, typeClean)
                .orElseGet(() -> {
                    PatrolGroupStt s = new PatrolGroupStt();
                    s.setFac(facClean);
                    s.setType(typeClean);
                    s.setCurrentStt(0);
                    return s;
                });

        int newStt = stt.getCurrentStt() + 1;
        stt.setCurrentStt(newStt);

        repo.save(stt);

        // push realtime
        messaging.convertAndSend(
                "/topic/stt/" + facClean + "/" + typeClean,
                newStt
        );

        log.info("Next STT | fac={} | type={} | stt={}", facClean, typeClean, newStt);

        return newStt;
    }

    // ===============================
    // LẤY STT HIỆN TẠI
    // ===============================
    @Transactional(readOnly = true)
    public int getCurrentByFacAndType(String fac, String type) {

        String facClean = fac.trim();
        String typeClean = type.trim();

        return repo
                .getCurrentByFacAndType(facClean, typeClean)
                .orElse(0);
    }
}
