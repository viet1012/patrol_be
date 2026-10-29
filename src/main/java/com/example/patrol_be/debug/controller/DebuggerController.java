package com.example.patrol_be.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DebuggerController {

    @GetMapping("/api/debug/headers")
    public Map<String, String> debugHeaders(HttpServletRequest request) {
        System.out.println("===== HEADERS FROM ENTRA =====");

        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            System.out.println(name + " = " + value);
            headers.put(name, value);
        }

        return headers; // trả JSON luôn cho bạn nhìn trên browser
    }
}