package com.example.rag_demo.repositories;

import com.example.rag_demo.entity.Elastic;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ElasticRepository extends ElasticsearchRepository<Elastic, Integer> {
    List<Elastic> findByContentContaining(String keyword);
}
