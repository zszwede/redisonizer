package com.example.redis.config;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Map;
@Data
@Component
public class ProcessorConfig {
    public static final String STRUCTURE_TYPE_MAP = "MAP";
    public static final String STRUCTURE_TYPE_LIST = "LIST";
    private StructureEntry root;
    private String redisHost;
    private int redisPort;

    private String keysSeparator;

    private String sliceEntryFormat;

    private Map<String, Function> functions;

    public StructureEntry getLevelEntries(String level){
        return root.getChildren().getOrDefault(level,null);
    }

    public Function getFunction(String fid){
        return functions.getOrDefault(fid, null);
    }
}
