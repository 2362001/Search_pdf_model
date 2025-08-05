package com.example.rag_demo.controller;

import com.example.rag_demo.entity.SearchRequest;
import com.example.rag_demo.entity.SearchResult;
import com.example.rag_demo.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final FileService fileService;

    @PostMapping("/search")
    public List<SearchResult> search(@RequestBody SearchRequest request) {
        return fileService.search(request.getQuery());
    }
}