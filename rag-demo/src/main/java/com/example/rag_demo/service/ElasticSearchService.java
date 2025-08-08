package com.example.rag_demo.service;

import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import com.example.rag_demo.entity.Elastic;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

@Service
public class ElasticSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public SearchHits<Elastic> searchByContent(String keyword) {
        Query query = Query.of(q ->
                q.bool(b -> b
                        .should(s -> s.match(m -> m
                                .field("CONTENT")
                                .query(keyword)
                                .minimumShouldMatch("100%") // hoặc 80%, 90% tùy bạn
                        ))
                        .should(s -> s.matchPhrase(mp -> mp
                                .field("CONTENT")
                                .query(keyword)
                                .boost(2.0f)
                        ))
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .build();

        System.out.println("---- Running search with keyword: " + keyword + " ----");

        SearchHits<Elastic> hits = elasticsearchOperations.search(nativeQuery, Elastic.class);

        hits.forEach(hit -> System.out.println(">> " + hit.getContent()));

        return hits;
    }
}