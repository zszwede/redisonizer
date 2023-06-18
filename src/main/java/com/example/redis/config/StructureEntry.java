package com.example.redis.config;

import lombok.Data;

import java.util.Map;

@Data
public class StructureEntry {
    private String type;
    private Map<String,StructureEntry> children;
}
