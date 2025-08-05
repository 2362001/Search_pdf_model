package com.example.rag_demo.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "chunks")
public class FileChunk {
    @Id
    private String id;

    private String fileName;
    private String content;
    private String contentNoDiacritics;

    private List<Double> embedding;
}
