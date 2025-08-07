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

    // Xử lý file đầu vào và lưu nội dung vào MongoDB sau khi chuẩn hóa (loại bỏ dấu tiếng Việt nếu cần thiết)
    public void processFile(MultipartFile file) throws IOException, TikaException {

        // Bước 1: Trích xuất nội dung văn bản từ file sử dụng Apache Tika
        String content = extractText(file);

        // Bước 2: Kiểm tra nếu file là JSON
        if (file.getOriginalFilename().endsWith(".json")) {

            // Dùng ObjectMapper để đọc nội dung JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(content); // Phân tích JSON thành cây JsonNode

            // Bước 3: Nếu JSON là một mảng (array)
            if (root.isArray()) {
                for (JsonNode node : root) {
                    // Chuyển từng phần tử JsonNode thành chuỗi JSON
                    String jsonObject = mapper.writeValueAsString(node);

                    // Chuẩn hóa nội dung bằng cách loại bỏ dấu tiếng Việt
                    String normalized = removeVietnameseDiacritics(jsonObject);

                    // Tạo đối tượng FileChunk để lưu vào MongoDB
                    FileChunk chunk = new FileChunk();
                    chunk.setFileName(file.getOriginalFilename());       // Tên file gốc
                    chunk.setContent(jsonObject);                        // Nội dung gốc của JSON object
                    chunk.setContentNoDiacritics(normalized);            // Nội dung đã loại bỏ dấu

                    // Lưu vào MongoDB
                    mongoTemplate.save(chunk);
                }
            } else {
                // Nếu JSON là một object (không phải array)
                String normalized = removeVietnameseDiacritics(content);

                // Tạo đối tượng FileChunk với nội dung gốc và nội dung đã chuẩn hóa
                FileChunk chunk = new FileChunk();
                chunk.setFileName(file.getOriginalFilename());
                chunk.setContent(content);
                chunk.setContentNoDiacritics(normalized);

                // Lưu vào MongoDB
                mongoTemplate.save(chunk);
            }
        } else {
            // Bước 4: Nếu file không phải JSON (có thể là .txt, .docx, v.v.)
            String contentNoDiacritics = removeVietnameseDiacritics(content);

            // Tạo đối tượng FileChunk để lưu vào MongoDB
            FileChunk chunk = new FileChunk();
            chunk.setFileName(file.getOriginalFilename());
            chunk.setContent(content);                      // Nội dung gốc
            chunk.setContentNoDiacritics(contentNoDiacritics); // Nội dung đã chuẩn hóa

            // Lưu vào MongoDB
            mongoTemplate.save(chunk);
        }
    }


    // Phương thức dùng để trích xuất nội dung văn bản từ một file upload (MultipartFile)
    private String extractText(MultipartFile file) throws IOException, TikaException {

        // Dùng Apache Tika để phát hiện loại MIME (MIME type) của file
        String mimeType = new Tika().detect(file.getInputStream());

        // ===== Xử lý file ảnh =====
        if (mimeType.startsWith("image/")) {
            // Tạo một ByteArrayResource từ nội dung file để phục vụ cho OCR (nhận diện ký tự từ hình ảnh)
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                // Ghi đè phương thức getFilename để trả về tên gốc của file
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            // Gọi hàm xử lý OCR (nhận diện ký tự từ hình ảnh)
            return extractTextFromImage(resource, file.getOriginalFilename());
        }

        // ===== Xử lý file âm thanh =====
        else if (mimeType.startsWith("audio/")) {
            // Gọi hàm chuyển giọng nói thành văn bản (speech-to-text)
            return transcribeAudio(file);
        }

        // ===== Xử lý file PDF =====
        else if (mimeType.equals("application/pdf")) {
            // Gọi hàm trích xuất nội dung từ file PDF
            return extractTextFromPdf(file);
        }

        // ===== Xử lý file JSON =====
        else if (mimeType.equals("application/json") || file.getOriginalFilename().endsWith(".json")) {
            // Đọc trực tiếp nội dung JSON thành chuỗi UTF-8
            return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        // ===== Trường hợp còn lại (fallback) =====
        // Nếu không thuộc các loại trên, sử dụng Tika để cố gắng trích xuất văn bản nói chung (tự động)
        return new Tika().parseToString(file.getInputStream());
    }

    // Phương thức trích xuất văn bản từ file PDF bằng cách chuyển từng trang thành ảnh và dùng OCR
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        // Danh sách chứa văn bản OCR của từng trang PDF
        List<String> pageTexts = new ArrayList<>();

        // Dùng try-with-resources để tự động đóng file PDF sau khi xử lý
        try (PDDocument document = PDDocument.load(file.getInputStream())) {

            // Tạo renderer để chuyển đổi các trang PDF thành hình ảnh
            PDFRenderer renderer = new PDFRenderer(document);

            // Duyệt qua từng trang của tài liệu PDF
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                int pageIndex = i; // Lưu index hiện tại của trang (dùng trong nội bộ lambda)

                // Render trang PDF thành hình ảnh ở độ phân giải 300 DPI (chất lượng cao để OCR chính xác hơn)
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);

                // Ghi hình ảnh sang định dạng PNG vào một ByteArrayOutputStream
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos); // Viết hình ảnh vào stream
                baos.flush();                      // Đảm bảo dữ liệu đã được ghi hoàn tất
                byte[] imageBytes = baos.toByteArray(); // Lấy dữ liệu hình ảnh dạng byte[]

                // Tạo ByteArrayResource từ byte[] để gửi sang OCR service
                ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
                    // Ghi đè getFilename để gán tên file mô phỏng cho ảnh (ví dụ: page0.png)
                    @Override
                    public String getFilename() {
                        return "page" + pageIndex + ".png";
                    }
                };

                // Gửi ảnh sang dịch vụ OCR để nhận lại văn bản
                String ocrText = sendImageToOcrService(imageResource);

                // Thêm kết quả OCR của trang vào danh sách
                pageTexts.add(ocrText);
            }
        } catch (Exception e) {
            // In stack trace và ném ra IOException nếu có lỗi xảy ra trong quá trình xử lý
            e.printStackTrace();
            throw new IOException("Lỗi khi xử lý PDF", e);
        }

        // Ghép văn bản của các trang lại với nhau, ngăn cách bằng dấu phân trang
        return String.join("\n\n--- PAGE BREAK ---\n\n", pageTexts);
    }


    // Gửi ảnh sang dịch vụ OCR qua HTTP (WebClient) và nhận lại văn bản được nhận diện
    private String sendImageToOcrService(ByteArrayResource imageResource) {

        // Khởi tạo WebClient với địa chỉ gốc là http://localhost:5000
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:5000")
                .build();

        // Gửi HTTP POST đến endpoint /read-text với hình ảnh dưới dạng multipart/form-data
        return webClient.post()
                .uri("/read-text") // endpoint OCR
                .contentType(MediaType.MULTIPART_FORM_DATA) // Định dạng gửi là multipart
                .body(BodyInserters.fromMultipartData("image", imageResource)) // Gửi hình ảnh với key "image"
                .retrieve() // Gửi request và nhận phản hồi
                .bodyToMono(String.class) // Nhận phản hồi dạng chuỗi JSON
                .map(response -> {
                    try {
                        // Dùng Jackson để parse chuỗi JSON trả về
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode node = mapper.readTree(response);

                        // Trích xuất trường "ai_corrected_text" chứa kết quả văn bản đã nhận diện và hiệu chỉnh
                        return node.get("ai_corrected_text").asText();
                    } catch (Exception e) {
                        // Nếu có lỗi khi parse JSON, trả về chuỗi rỗng
                        return "";
                    }
                })
                .block(); // block để chờ kết quả đồng bộ (do hàm này trả về String)
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