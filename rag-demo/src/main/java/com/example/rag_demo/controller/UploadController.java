package com.example.rag_demo.controller;

import com.example.rag_demo.entity.FileChunk;
import com.example.rag_demo.service.FileService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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

    @GetMapping("/files")
    public ResponseEntity<List<FileChunk>> getAllFiles() {
        List<FileChunk> files = fileService.getAllFiles();
        return ResponseEntity.ok(files);
    }
}
