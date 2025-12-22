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
            @RequestParam String type
    ) {
        return sttService.getCurrentByFacAndType(fac,type);
    }


}
