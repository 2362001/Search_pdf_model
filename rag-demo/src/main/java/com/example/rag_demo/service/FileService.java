package com.example.rag_demo.service;

import com.example.rag_demo.entity.FileChunk;
import com.example.rag_demo.entity.FileChunkWithScore;
import com.example.rag_demo.entity.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.bson.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final MongoTemplate mongoTemplate;
    private final EmbeddingClient embeddingClient;

    public void processFile(MultipartFile file) throws IOException, TikaException {
        String content = extractText(file);
        String contentNoDiacritics = removeVietnameseDiacritics(content);

        FileChunk fileChunk = new FileChunk();
        fileChunk.setFileName(file.getOriginalFilename());
        fileChunk.setContent(content);
        fileChunk.setContentNoDiacritics(contentNoDiacritics); // thêm dòng này

        mongoTemplate.save(fileChunk);
    }

    public static String removeVietnameseDiacritics(String text) {
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")  // Remove diacritic marks
                .replaceAll("đ", "d")      // Replace đ
                .replaceAll("Đ", "D");     // Replace Đ
    }

    private String extractText(MultipartFile file) throws IOException, TikaException {
        String mimeType = new Tika().detect(file.getInputStream());

        if (mimeType.startsWith("image/")) {
//            return extractTextFromImage(file);
        } else if (mimeType.startsWith("audio/")) {
            return transcribeAudio(file); // gọi Whisper để chuyển âm thanh thành text
        }

        // Mặc định: văn bản
        return new Tika().parseToString(file.getInputStream());
    }

    private String transcribeAudio(MultipartFile file) throws IOException {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:5005") // URL service Python Whisper
                .build();

        return webClient.post()
                .uri("/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file",
                        new ByteArrayResource(file.getBytes()) {
                            @Override
                            public String getFilename() {
                                return file.getOriginalFilename();
                            }
                        }))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode node = mapper.readTree(response);
                        return node.get("text").asText();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .block();
    }


    private List<String> splitIntoChunks(String text, int maxWords) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < words.length; i += maxWords) {
            chunks.add(String.join(" ", Arrays.copyOfRange(words, i, Math.min(i + maxWords, words.length))));
        }
        return chunks;
    }

    public List<SearchResult> search(String query) {
        List<Double> queryVector = embeddingClient.getEmbedding(query);

        List<Document> pipeline = List.of(
                new Document("$vectorSearch",
                        new Document("index", "vector_index")
                                .append("path", "embedding")
                                .append("queryVector", queryVector)
                                .append("numCandidates", 100)
                                .append("limit", 30)
                ),
                new Document("$project",
                        new Document("fileName", 1)
                                .append("score", new Document("$meta", "vectorSearchScore"))
                                .append("source", "vector")
                ),
                new Document("$unionWith",
                        new Document("coll", "chunks")
                                .append("pipeline", List.of(
                                        new Document("$search", new Document()
                                                .append("index", "default")
                                                .append("text", new Document()
                                                        .append("query", query)
                                                        .append("path", List.of("content", "fileName"))
                                                )
                                        ),
                                        new Document("$project", new Document()
                                                .append("fileName", 1)
                                                .append("score", new Document("$meta", "searchScore"))
                                                .append("source", "text")
                                        )
                                ))
                ),
                new Document("$sort", new Document("score", -1)),
                new Document("$limit", 50)
        );

        Aggregation aggregation = Aggregation.newAggregation(
                pipeline.stream().map((Document stage) -> (AggregationOperation) context -> stage)
                        .collect(Collectors.toList())
        );

        AggregationResults<FileChunkWithScore> results = mongoTemplate.aggregate(
                aggregation, "chunks", FileChunkWithScore.class
        );

        Map<String, FileChunkWithScore> topChunkByFile = new HashMap<>();
        for (FileChunkWithScore chunk : results.getMappedResults()) {
            String fileName = chunk.getFileName();
            if (!topChunkByFile.containsKey(fileName) || chunk.getScore() > topChunkByFile.get(fileName).getScore()) {
                topChunkByFile.put(fileName, chunk);
            }
        }

        return topChunkByFile.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .map(c -> new SearchResult(c.getFileName(), c.getScore()))
                .collect(Collectors.toList());
    }



}