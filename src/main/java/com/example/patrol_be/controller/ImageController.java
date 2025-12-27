//package com.example.patrol_be.controller;
//
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.UrlResource;
//import org.springframework.http.*;
//import org.springframework.web.bind.annotation.*;
//
//import java.io.IOException;
//import java.nio.file.*;
//import java.nio.file.Paths;
//
//@RestController
//public class ImageController {
//
////    private static final Path IMAGE_DIR =
////            Paths.get("D:/1.pc/Patrol App/uploaded_images");
//
//        private static final Path IMAGE_DIR =
//            Paths.get("C:/Users/viet.ta/Downloads/patrol_be/uploaded_images");
//    @GetMapping("/images/{filename:.+}")
//    public ResponseEntity<Resource> image(@PathVariable String filename)
//            throws IOException {
//
//        Path file = IMAGE_DIR.resolve(filename);
//
//        if (!Files.exists(file)) {
//            return ResponseEntity.notFound().build();
//        }
//
//        Resource resource = new UrlResource(file.toUri());
//
//        return ResponseEntity.ok()
//                .contentType(MediaType.IMAGE_JPEG)
//                .header(HttpHeaders.CACHE_CONTROL, "no-store")
//                .body(resource);
//    }
//}
