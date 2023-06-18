package com.example.redis.processors;

import com.example.redis.config.LookupResult;
import com.example.redis.config.StructureEntry;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@SpringBootTest(classes = RedisProcessor.class)
public class ProcessorTest {
    @Autowired
    private RedisProcessor rp;

    @Test
    public void PopulatePayloadTest() throws Exception{
        Path path = Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("payload.json")).toURI());
        byte[] bytes = Files.readAllBytes(path);
        Map<String,Object> obj = new Gson().fromJson(new String(bytes), Map.class);
        rp.PopulatePayload("AAAA", obj);
    }

    @Test
    public void Evict(){
        ArrayList<String> keys = new ArrayList<>();
        rp.GetEvictKeys("AAAA", rp.processorConfig.getRoot(), keys);
        Assertions.assertEquals(12, keys.size());
        //rp.removeKeys(keys);
    }

    @Test
    public void GetPathsTest(){
        LinkedList<String> ll = new LinkedList<>();
        ll.add("map2");
        ll.add("slice1");
        //ll.add("slicenestedslice");
        StructureEntry structureForLevels = rp.getStructureForLevels(rp.processorConfig.getRoot(), ll);
        LookupResult lr = new LookupResult();
        lr.setKeys(new LinkedList<>());
        rp.GetPathKeys("AAAA",rp.processorConfig.getRoot(), ll, lr);
        List<Map<String,Object>> entries = new ArrayList<>();
        for (String key : lr.getKeys()) {
            HashMap<String, Object> result = new HashMap<>();
            rp.GetObject(key, lr.getTargetStructure(), true, false, result);
            entries.add(result);
        }
        Object read = JsonPath.read(entries, "$[?(@.sliceprop3 == 'sp13')]");
        System.out.println(read);
    }

    @Test
    public void GetPathsTest2(){
        LinkedList<String> ll = new LinkedList<>();
        ll.add("map2");
        ll.add("slice1");
        //ll.add("slicenestedslice");
        //StructureEntry structureForLevels = rp.getStructureForLevels(rp.processorConfig.getRoot(), new LinkedList<>(ll));
        LookupResult lr = new LookupResult();
        lr.setKeys(new LinkedList<>());
        rp.GetPathKeys("AAAA",rp.processorConfig.getRoot(), ll, lr);
        List<Map<String,Object>> entries = new ArrayList<>();
        for (String key : lr.getKeys()) {
            Map<String, Object> result = rp.GetObject2(key, lr.getTargetStructure(), true, false);
            entries.add(result);
        }
        Object read = JsonPath.read(entries, "$[?(@.sliceprop3 == 'sp13')]");
        System.out.println(read);
    }

    @Test
    public void GetMapObjectRecurseTest(){
        HashMap<String, Object> result = new HashMap<>();
        rp.GetObject("AAAA",rp.processorConfig.getRoot(),true, false, result);
        System.out.println(result);
    }

    @Test
    public void GetMapObjectNonRecurseRefsTest(){
        HashMap<String, Object> result = new HashMap<>();
        rp.GetObject("AAAA",rp.processorConfig.getRoot(),false, true, result);
        System.out.println(result);
    }

    @Test
    public void GetMapObjectNonRecurseNoRefsTest(){
        HashMap<String, Object> result = new HashMap<>();
        rp.GetObject("AAAA",rp.processorConfig.getRoot(),false, false, result);
        System.out.println(result);
    }
}
