package com.example.redis.config;

import lombok.Data;

import java.util.ArrayList;

@Data
public class Function {
    ArrayList<String> levels;
    String query;
    boolean recurse;
    boolean refs;
}
