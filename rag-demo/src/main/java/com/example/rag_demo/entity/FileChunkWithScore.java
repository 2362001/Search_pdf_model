package com.example.rag_demo.entity;

import lombok.Data;

import java.util.List;

@Data
public class FileChunkWithScore {
    private String fileName;
    private String content;
    private List<Double> embedding;
    private Double score;
}