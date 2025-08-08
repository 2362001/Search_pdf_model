package com.example.rag_demo.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Data
@Document(indexName = "els_search")
public class Elastic {

    @Id
    private Integer id;

    private String ngaythaydoi;
    private String userid;

    @Field(name = "CONTENT", type = FieldType.Text)
    private String content;

    private Integer qdcbid;
    private Integer tthcid;
    private Integer active;
    private Integer type;

    @Field(name = "TTHC_COQUANCHUQUANID")
    private Integer tthcCoquanchuquanid;
}
