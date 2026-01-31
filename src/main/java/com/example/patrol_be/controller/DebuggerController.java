package com.example.patrol_be.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Enumeration;

public class DebuggerController {
    @GetMapping("/api/debug/headers")
    public void debugHeaders(HttpServletRequest request) {
        System.out.println("===== HEADERS FROM ENTRA =====");
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            System.out.println(name + " = " + request.getHeader(name));
        }
    }

}
