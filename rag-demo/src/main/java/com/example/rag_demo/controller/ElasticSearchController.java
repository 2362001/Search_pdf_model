package com.example.rag_demo.controller;

import com.example.rag_demo.entity.Elastic;
import com.example.rag_demo.service.ElasticSearchService;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/elastic/api/search")
public class ElasticSearchController {

    private final ElasticSearchService elasticSearchService;

    public ElasticSearchController(ElasticSearchService elasticSearchService) {
        this.elasticSearchService = elasticSearchService;
    }

    @GetMapping
    public List<Elastic> search(@RequestParam String keyword) {
        return elasticSearchService.searchByContent(keyword)
                .stream()
                .map(hit -> hit.getContent())
                .toList();
    }
}
