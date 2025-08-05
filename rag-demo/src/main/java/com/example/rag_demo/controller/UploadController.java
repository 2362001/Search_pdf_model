package com.example.rag_demo.controller;

import com.example.rag_demo.service.FileService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            fileService.processFile(file);
            return ResponseEntity.ok("File processed successfully.");
        } catch (IOException | TikaException e) {
            throw new RuntimeException("Failed to process file", e);
        }
    }
}
