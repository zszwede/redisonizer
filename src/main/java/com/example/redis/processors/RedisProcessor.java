package com.example.redis.processors;

import com.example.redis.config.Function;
import com.example.redis.config.LookupResult;
import com.example.redis.config.ProcessorConfig;
import com.example.redis.config.StructureEntry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.gson.GsonRuntime;
import org.springframework.stereotype.Component;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component("redisProcessor")
public class RedisProcessor {
    public final Gson gson = new Gson();


    public RedisProcessor(){
        try {
            Path path = Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("config.json")).toURI());
            byte[] bytes = Files.readAllBytes(path);
            processorConfig = gson.fromJson(new String(bytes), ProcessorConfig.class);
            jedis = new Jedis(new HostAndPort(processorConfig.getRedisHost(), processorConfig.getRedisPort()));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //@Autowired()
    public ProcessorConfig processorConfig;

    //@Autowired()
    private Jedis jedis;


//    @Bean()
//    @DependsOn({"config"})
//    public Jedis jedisCreator() {
//        return new Jedis(new HostAndPort(processorConfig.getRedisHost(), processorConfig.getRedisPort()));
//    }
//
//    @Bean(name = "config")
//    public ProcessorConfig processorConfig() throws IOException, URISyntaxException {
//        Path path = Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("config.json")).toURI());
//        byte[] bytes = Files.readAllBytes(path);
//        return gson.fromJson(new String(bytes), ProcessorConfig.class);
//    }

    public void GetEvictKeys(String key, StructureEntry entry, List<String> keys) {
        keys.add(key);
        if (entry.getChildren().size() == 0) {
            return;
        }
        for (Map.Entry<String, StructureEntry> childEntry : entry.getChildren().entrySet()) {
            StructureEntry child = childEntry.getValue();
            String structureKey = jedis.hget(key, childEntry.getKey());
            if (child.getType().equals(ProcessorConfig.STRUCTURE_TYPE_MAP)) {
                GetEvictKeys(structureKey, child, keys);
            } else if (child.getType().equals(ProcessorConfig.STRUCTURE_TYPE_LIST)) {
                keys.add(structureKey);
                Set<String> setObjectKeys = jedis.smembers(structureKey);
                for (String setObjectKey : setObjectKeys) {
                    GetEvictKeys(setObjectKey, child, keys);
                }
            }
        }
    }

    public Long removeKey(String key) {
        return jedis.del(key);
    }

    public Long removeKeys(Collection<String> keys) {
        String[] array = keys.toArray(new String[0]);
        return jedis.del(array);
    }

    public void PopulatePayload(String key, Map<String, Object> obj) {
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            if (entry.getValue() instanceof Map) {
                String newKey = getChildKey(key, entry.getKey().toUpperCase());
                //HSET
                jedis.hset(key, entry.getKey(), newKey);
                //System.out.printf("%s %s = %s%n", key, entry.getKey(), newKey);
                PopulatePayload(newKey, (Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                String ucaseKey = entry.getKey().toUpperCase();
                List<Map<String, Object>> l = (List<Map<String, Object>>) entry.getValue();
                String newKey = getChildKey(key, ucaseKey);
                //HSET
                jedis.hset(key, entry.getKey(), newKey);
                //System.out.printf("%s %s = %s%n", key, entry.getKey(), newKey);
                for (int i = 0; i < l.size(); i++) {
                    String entryKey = getEntryKey(key, ucaseKey, i);
                    //SADD
                    jedis.sadd(newKey, entryKey);
                    //System.out.printf("%s %s%n", newKey, entryKey);
                    PopulatePayload(entryKey, l.get(i));
                }

            } else {
                //HSET
                jedis.hset(key, entry.getKey(), entry.getValue().toString());
            }
        }
    }

    public String getChildKey(String parent, String child) {
        return format("%s%s%s", parent, processorConfig.getKeysSeparator(), child);
    }

    public String getEntryKey(String parent, String child, int index) {
        return getChildKey(getChildKey(parent, child), format(processorConfig.getSliceEntryFormat(), index));
    }

    public String processFunction(String id, String fid) throws Exception {
        Function fn = processorConfig.getFunction(fid);
        if(fn == null){
            throw new Exception();
        }
        LookupResult lr = new LookupResult();
        lr.setKeys(new ArrayList<>());
        GetPathKeys(id, processorConfig.getRoot(), new LinkedList<>(fn.getLevels()), lr);
        List<Object> result = lr.getKeys().stream().map(k -> GetObject2(k, lr.getTargetStructure(), fn.isRecurse(), fn.isRefs())).collect(Collectors.toList());
        if(fn.getJsonpathquery() != null){
            result = JsonPath.read(result, fn.getJsonpathquery());
        } else if (fn.getJmespathquery() != null) {
            JmesPath<JsonElement> jmespath = new GsonRuntime();
            JsonElement jsonTree = new Gson().toJsonTree(result);
            Expression<JsonElement> expression = jmespath.compile(fn.getJmespathquery());
            JsonElement output = expression.search(jsonTree);
            return gson.toJson(output);
        }
        return gson.toJson(result);
    }

    public void GetObject(String key, StructureEntry entry, boolean recurse, boolean refs, Map<String, Object> result) {
        Map<String, String> fields = jedis.hgetAll(key);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (entry.getChildren().containsKey(field.getKey()) && recurse) {
                StructureEntry struct = entry.getChildren().get(field.getKey());
                String refKey = jedis.hget(key, field.getKey());
                switch (struct.getType()) {
                    case ProcessorConfig.STRUCTURE_TYPE_MAP:
                        HashMap<String, Object> middleMap = new HashMap<>();
                        GetObject(refKey, struct, recurse, refs, middleMap);
                        result.put(field.getKey(), middleMap);
                        break;
                    case ProcessorConfig.STRUCTURE_TYPE_LIST:
                        ArrayList<Map<String, Object>> middleList = new ArrayList<>();
                        Set<String> setObjectKeys = jedis.smembers(refKey);
                        for (String setObjectKey : setObjectKeys) {
                            HashMap<String, Object> listMiddleMap = new HashMap<>();
                            GetObject(setObjectKey, struct, recurse, refs, listMiddleMap);
                            middleList.add(listMiddleMap);
                        }
                        result.put(field.getKey(), middleList);
                        break;
                    default:
                }
            } else {
                if (!refs && entry.getChildren().containsKey(field.getKey())) {
                    continue;
                }
                result.put(field.getKey(), field.getValue());
            }

        }
    }


    public Map<String, Object> GetObject2(String key, StructureEntry entry, boolean recurse, boolean refs) {
        HashMap<String, Object> result = new HashMap<>();
        Map<String, String> fields = jedis.hgetAll(key);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (entry.getChildren().containsKey(field.getKey()) && recurse) {
                StructureEntry struct = entry.getChildren().get(field.getKey());
                String refKey = jedis.hget(key, field.getKey());
                switch (struct.getType()) {
                    case ProcessorConfig.STRUCTURE_TYPE_MAP:
                        Map<String, Object> middleMap = GetObject2(refKey, struct, recurse, refs);
                        result.put(field.getKey(), middleMap);
                        break;
                    case ProcessorConfig.STRUCTURE_TYPE_LIST:
                        ArrayList<Map<String, Object>> middleList = new ArrayList<>();
                        Set<String> setObjectKeys = jedis.smembers(refKey);
                        for (String setObjectKey : setObjectKeys) {
                            Map<String, Object> lmap = GetObject2(setObjectKey, struct, recurse, refs);
                            middleList.add(lmap);
                        }
                        result.put(field.getKey(), middleList);
                        break;
                    default:
                }
            } else {
                if (!refs && entry.getChildren().containsKey(field.getKey())) {
                    continue;
                }
                result.put(field.getKey(), field.getValue());
            }
        }
        return result;
    }

    public void GetPathKeys(String key, StructureEntry entry, LinkedList<String> tags, LookupResult result) {
        if (tags.size() == 0) {
            result.getKeys().add(key);
            if (result.getTargetStructure() == null) {
                result.setTargetStructure(entry);
            }
            return;
        }
        if (entry.getChildren().size() == 0) {
            return;
        }
        String tag = tags.pop();
        StructureEntry child;
        child = entry.getChildren().get(tag);
        String structureKey = jedis.hget(key, tag);
        if (child.getType().equals(ProcessorConfig.STRUCTURE_TYPE_MAP)) {
            GetPathKeys(structureKey, child, tags, result);
        } else if (child.getType().equals(ProcessorConfig.STRUCTURE_TYPE_LIST)) {
            Set<String> setObjectKeys = jedis.smembers(structureKey);
            for (String setObjectKey : setObjectKeys) {
                LinkedList<String> tagCopy = new LinkedList<>(tags);
                GetPathKeys(setObjectKey, child, tagCopy, result);
            }
        }
    }

    public StructureEntry getStructureForLevels(StructureEntry parent, LinkedList<String> levels){
        String l = levels.pop();
        Optional<StructureEntry> first = parent.getChildren().entrySet().stream().filter(e -> e.getKey().equals(l)).map(Map.Entry::getValue).findFirst();
        if(first.isPresent()){
            StructureEntry found = first.get();
            if(levels.isEmpty()){
                return found;
            }
            return getStructureForLevels(found, levels);
        }else{
            return null;
        }
    }

    public void getRootStructureFromMap(Map<String, Object> obj, StructureEntry parent){
        parent.setChildren(new HashMap<>());
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            if (entry.getValue() instanceof Map) {
                StructureEntry child = new StructureEntry();
                child.setType(ProcessorConfig.STRUCTURE_TYPE_MAP);
                getRootStructureFromMap((Map<String, Object>) entry.getValue(), child);
                parent.getChildren().put(entry.getKey(), child);
            } else if (entry.getValue() instanceof List) {
                StructureEntry child = new StructureEntry();
                child.setType(ProcessorConfig.STRUCTURE_TYPE_LIST);
                List<Map<String, Object>> l = (List<Map<String, Object>>) entry.getValue();
                getRootStructureFromMap(l.get(0), child);
                parent.getChildren().put(entry.getKey(), child);
            }
        }
    }

}
