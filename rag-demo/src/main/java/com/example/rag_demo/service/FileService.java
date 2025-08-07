package com.example.rag_demo.service;

import com.example.rag_demo.entity.FileChunk;
import com.example.rag_demo.entity.FileChunkWithScore;
import com.example.rag_demo.entity.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final MongoTemplate mongoTemplate;

    public static String removeVietnameseDiacritics(String text) {
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replaceAll("đ", "d").replaceAll("Đ", "D");
    }

    public void processFile(MultipartFile file) throws IOException, TikaException {
        String content = extractText(file);
        if (file.getOriginalFilename().endsWith(".json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(content);

            if (root.isArray()) {
                for (JsonNode node : root) {
                    String jsonObject = mapper.writeValueAsString(node);
                    String normalized = removeVietnameseDiacritics(jsonObject);

                    FileChunk chunk = new FileChunk();
                    chunk.setFileName(file.getOriginalFilename());
                    chunk.setContent(jsonObject);
                    chunk.setContentNoDiacritics(normalized);

                    mongoTemplate.save(chunk);
                }
            } else {
                String normalized = removeVietnameseDiacritics(content);
                FileChunk chunk = new FileChunk();
                chunk.setFileName(file.getOriginalFilename());
                chunk.setContent(content);
                chunk.setContentNoDiacritics(normalized);
                mongoTemplate.save(chunk);
            }
        } else {
            String contentNoDiacritics = removeVietnameseDiacritics(content);
            FileChunk chunk = new FileChunk();
            chunk.setFileName(file.getOriginalFilename());
            chunk.setContent(content);
            chunk.setContentNoDiacritics(contentNoDiacritics);
            mongoTemplate.save(chunk);
        }
    }

    private String extractText(MultipartFile file) throws IOException, TikaException {
        String mimeType = new Tika().detect(file.getInputStream());

        if (mimeType.startsWith("image/")) {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            return extractTextFromImage(resource, file.getOriginalFilename());
        } else if (mimeType.startsWith("audio/")) {
            return transcribeAudio(file);
        } else if (mimeType.equals("application/pdf")) {
            return extractTextFromPdf(file);
        } else if (mimeType.equals("application/json") || file.getOriginalFilename().endsWith(".json")) {
            // ✅ Đọc toàn bộ nội dung JSON thành chuỗi
            return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        // fallback
        return new Tika().parseToString(file.getInputStream());
    }

    private String extractTextFromPdf(MultipartFile file) throws IOException {
        List<String> pageTexts = new ArrayList<>();

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                int pageIndex = i;

                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                baos.flush();
                byte[] imageBytes = baos.toByteArray();

                ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return "page" + pageIndex + ".png";
                    }
                };

                String ocrText = sendImageToOcrService(imageResource);
                pageTexts.add(ocrText);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Lỗi khi xử lý PDF", e);
        }

        return String.join("\n\n--- PAGE BREAK ---\n\n", pageTexts);
    }

    private String sendImageToOcrService(ByteArrayResource imageResource) {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:5000").build();

        return webClient.post().uri("/read-text").contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData("image", imageResource)).retrieve().bodyToMono(String.class).map(response -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(response);
                return node.get("ai_corrected_text").asText();
            } catch (Exception e) {
                return "";
            }
        }).block();
    }


    private String extractTextFromImage(ByteArrayResource imageResource, String filename) throws IOException {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:5000")
                .build();

        return webClient.post().uri("/read-text").contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData("image", new ByteArrayResource(imageResource.getByteArray()) {
            @Override
            public String getFilename() {
                return filename;
            }
        })).retrieve().bodyToMono(String.class).map(response -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(response);
                return node.get("ai_corrected_text").asText();
            } catch (Exception e) {
                return "";
            }
        }).block();
    }

    private String transcribeAudio(MultipartFile file) throws IOException {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:5001")
                .build();

        return webClient.post().uri("/transcribe").contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        })).retrieve().bodyToMono(String.class).map(response -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(response);
                return node.get("text").asText();
            } catch (Exception e) {
                return "";
            }
        }).block();
    }

//    public List<SearchResult> search(String query) {
//        List<Double> queryVector = embeddingClient.getEmbedding(query);
//
//        List<Document> pipeline = List.of(
//                new Document("$vectorSearch",
//                        new Document("index", "vector_index")
//                                .append("path", "embedding")
//                                .append("queryVector", queryVector)
//                                .append("numCandidates", 100)
//                                .append("limit", 30)
//                ),
//                new Document("$project",
//                        new Document("fileName", 1)
//                                .append("score", new Document("$meta", "vectorSearchScore"))
//                                .append("source", "vector")
//                ),
//                new Document("$unionWith",
//                        new Document("coll", "chunks")
//                                .append("pipeline", List.of(
//                                        new Document("$search", new Document()
//                                                .append("index", "default")
//                                                .append("text", new Document()
//                                                        .append("query", query)
//                                                        .append("path", List.of("content", "fileName"))
//                                                )
//                                        ),
//                                        new Document("$project", new Document()
//                                                .append("fileName", 1)
//                                                .append("score", new Document("$meta", "searchScore"))
//                                                .append("source", "text")
//                                        )
//                                ))
//                ),
//                new Document("$sort", new Document("score", -1)),
//                new Document("$limit", 50)
//        );
//
//        Aggregation aggregation = Aggregation.newAggregation(
//                pipeline.stream().map((Document stage) -> (AggregationOperation) context -> stage)
//                        .collect(Collectors.toList())
//        );
//
//        AggregationResults<FileChunkWithScore> results = mongoTemplate.aggregate(
//                aggregation, "chunks", FileChunkWithScore.class
//        );
//
//        Map<String, FileChunkWithScore> topChunkByFile = new HashMap<>();
//        for (FileChunkWithScore chunk : results.getMappedResults()) {
//            String fileName = chunk.getFileName();
//            if (!topChunkByFile.containsKey(fileName) || chunk.getScore() > topChunkByFile.get(fileName).getScore()) {
//                topChunkByFile.put(fileName, chunk);
//            }
//        }
//
//        return topChunkByFile.values().stream()
//                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
//                .map(c -> new SearchResult(c.getFileName(), c.getScore()))
//                .collect(Collectors.toList());
//    }

    public List<SearchResult> search(String query) {
        String queryNoDiacritics = removeVietnameseDiacritics(query);

        List<Document> pipeline = List.of(
                new Document("$match", new Document("$or", List.of(
                        new Document("content", new Document("$regex", query).append("$options", "i")),
                        new Document("contentNoDiacritics", new Document("$regex", queryNoDiacritics).append("$options", "i"))
                ))),
                new Document("$project", new Document("fileName", 1).append("source", "regex").append("content", 1)),
                new Document("$limit", 50)
        );

        Aggregation aggregation = Aggregation.newAggregation(
                pipeline.stream().map((Document stage) -> (AggregationOperation) context -> stage)
                        .collect(Collectors.toList())
        );

        AggregationResults<FileChunkWithScore> results = mongoTemplate.aggregate(aggregation, "chunks", FileChunkWithScore.class);

        Set<String> seenFiles = new HashSet<>();
        List<SearchResult> finalResults = new ArrayList<>();
        for (FileChunkWithScore chunk : results.getMappedResults()) {
            finalResults.add(new SearchResult(
                    chunk.getFileName(),
                    chunk.getContent()
            ));
        }

        return finalResults;
    }


    public List<FileChunk> getAllFiles() {
        return mongoTemplate.findAll(FileChunk.class, "chunks");
    }
}