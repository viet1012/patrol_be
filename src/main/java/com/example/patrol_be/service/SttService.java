package com.example.patrol_be.service;

import com.example.patrol_be.model.PatrolGroupStt;
import com.example.patrol_be.repository.PatrolGroupSttRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // ✅ ĐÚNG

import java.time.LocalDate;
import java.util.Comparator;

import lombok.extern.slf4j.Slf4j;
@Service
@RequiredArgsConstructor
@Slf4j
public class SttService {

    private final PatrolGroupSttRepo repo;
    private final SimpMessagingTemplate messaging;

//    @Transactional
//    public int next(String fac, String grp) {
//        LocalDate today = LocalDate.now();
//
//        // Lo?i b? d?u cách trong fac và grp
//        final String facClean = fac.replace(" ", "").trim();
//        final String grpClean = grp.replace(" ", "").trim();
//
//        PatrolGroupStt stt = repo
//                .findByFacAndGrp( facClean, grpClean ) // ✅ FIX
//                .orElseGet(() -> {
//                    PatrolGroupStt s = new PatrolGroupStt();
//                    s.setWorkDate(today);
//                    s.setFac(facClean);
//                    s.setGrp(grpClean);
//                    s.setCurrentStt(0);
//                    return s;
//                });
//
//        int newStt = stt.getCurrentStt() + 1;
//        stt.setCurrentStt(newStt);
//
//        repo.save(stt);
//
//        messaging.convertAndSend(
//                "/topic/stt/" + facClean + "/" + grpClean,
//                newStt
//        );
//
//        return newStt;
//    }
//
//    @Transactional
//    public int getCurrent(String fac, String grp) {
//        LocalDate today = LocalDate.now();
//        final String facClean = fac.replace(" ", "").trim();
//        final String grpClean = grp.replace(" ", "").trim();
//
//        return repo
//                .findByWorkDateAndFacAndGrp(today, facClean, grpClean)
//                .map(PatrolGroupStt::getCurrentStt)
//                .orElse(0);
//    }
//
//    @Transactional
//    public int nextByFac(String fac, String grp) {
//
//        String facClean = fac.trim();
//        final String grpClean = grp.replace(" ", "").trim();
//
//        // 🔒 Lock + lấy max hiện tại
//        Integer max = repo.findMaxSttForUpdate(facClean);
//        int newStt = (max == null ? 1 : max + 1);
//
//        // Lấy 1 record để update (hoặc tạo mới)
//        PatrolGroupStt stt = repo.findLatestByFac(facClean)
//                .stream()
//                .findFirst()
//                .orElseGet(() -> {
//                    PatrolGroupStt s = new PatrolGroupStt();
//                    s.setFac(facClean);
//                    s.setGrp(grpClean); // giữ cột nhưng không dùng
//                    s.setWorkDate(LocalDate.now());
//                    s.setCurrentStt(0);
//                    return s;
//                });
//
//        stt.setCurrentStt(newStt);
//        repo.save(stt);
//
//        messaging.convertAndSend(
//                "/topic/stt/" + facClean,
//                newStt
//        );
//        String topic = "/topic/stt/" + facClean;
//
//        log.info("📢 WS SEND -> topic={}, message={}", topic, newStt);
//        return newStt;
//    }

    @Transactional
    public int nextByFac(String fac) {
        String facClean = fac.trim();

        PatrolGroupStt stt = repo.findForUpdateByFac(facClean)
                .orElseGet(() -> {
                    PatrolGroupStt s = new PatrolGroupStt();
                    s.setFac(facClean);
                    s.setCurrentStt(0);
                    return s;
                });

        int newStt = stt.getCurrentStt() + 1;
        stt.setCurrentStt(newStt);

        repo.save(stt);

        messaging.convertAndSend(
                "/topic/stt/" + facClean,
                newStt
        );

        return newStt;
    }


    @Transactional(readOnly = true)
    public int getCurrentByFac(String fac) {
        String facClean = fac.trim();

        return repo.getCurrentByFac(facClean)
                .orElse(0);
    }



}
