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

        LocalDate today = LocalDate.now(); // 👉 ngày hiện tại

        PatrolGroupStt stt = repo
                .findByWorkDateAndFacAndGrp(today, fac, grp)
                .orElseGet(() -> {
                    PatrolGroupStt s = new PatrolGroupStt();
                    s.setWorkDate(today);
                    s.setFac(fac);
                    s.setGrp(grp);
                    s.setCurrentStt(0);
                    return s;
                });

        int newStt = stt.getCurrentStt() + 1;
        stt.setCurrentStt(newStt);

        repo.save(stt);

        // 🔥 broadcast realtime theo Fac + Group
        messaging.convertAndSend(
                "/topic/stt/" + fac + "/" + grp,
                newStt
        );

        return newStt;
    }

    @Transactional
    public int getCurrent(String fac, String grp) {
        LocalDate today = LocalDate.now();


        return repo
                .findByWorkDateAndFacAndGrp(today, fac, grp)
                .map(PatrolGroupStt::getCurrentStt)
                .orElse(0);
    }

}
