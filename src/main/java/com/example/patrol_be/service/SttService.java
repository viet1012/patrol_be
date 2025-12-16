package com.example.patrol_be.service;

import com.example.patrol_be.model.PatrolGroupStt;
import com.example.patrol_be.repository.PatrolGroupSttRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
@Service
@RequiredArgsConstructor
public class SttService {

    private final PatrolGroupSttRepo repo;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public int next(String fac, String grp) {
        LocalDate today = LocalDate.now();

        // Lo?i b? d?u cách trong fac và grp
        final String facClean = fac.replace(" ", "").trim();
        final String grpClean = grp.replace(" ", "").trim();

        PatrolGroupStt stt = repo
                .findByWorkDateAndFacAndGrp(today, facClean, grpClean) // ✅ FIX
                .orElseGet(() -> {
                    PatrolGroupStt s = new PatrolGroupStt();
                    s.setWorkDate(today);
                    s.setFac(facClean);
                    s.setGrp(grpClean);
                    s.setCurrentStt(0);
                    return s;
                });

        int newStt = stt.getCurrentStt() + 1;
        stt.setCurrentStt(newStt);

        repo.save(stt);

        messaging.convertAndSend(
                "/topic/stt/" + facClean + "/" + grpClean,
                newStt
        );

        return newStt;
    }

    @Transactional
    public int getCurrent(String fac, String grp) {
        LocalDate today = LocalDate.now();
        final String facClean = fac.replace(" ", "").trim();
        final String grpClean = grp.replace(" ", "").trim();

        return repo
                .findByWorkDateAndFacAndGrp(today, facClean, grpClean)
                .map(PatrolGroupStt::getCurrentStt)
                .orElse(0);
    }

}
