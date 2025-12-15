package com.example.patrol_be.controller;
import com.example.patrol_be.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stt")
public class SttController {

    private final SttService sttService;

    @GetMapping("/crt")
    public int getCurrent(
            @RequestParam String fac,
            @RequestParam String grp
    ) {
        return sttService.getCurrent(fac, grp);
    }


//    /** ➕ TĂNG STT + TRẢ VỀ STT MỚI */
//    @PostMapping("/{group}/next")
//    public int nextStt(@PathVariable String group) {
//        return sttService.next(group);
//    }
}
