package com.example.redis.config;

import lombok.Data;

import java.util.List;

@Data
public class LookupResult {
    List<String> keys;
    StructureEntry targetStructure;
}
