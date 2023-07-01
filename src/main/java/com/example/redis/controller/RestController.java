package com.example.redis.controller;


import com.example.redis.config.ProcessorConfig;
import com.example.redis.config.StructureEntry;
import com.example.redis.processors.RedisProcessor;
import com.github.underscore.U;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class RestController {

    private final RedisProcessor rp;

    @Autowired
    public RestController(RedisProcessor rp) {
        this.rp = rp;
    }

    @GetMapping("/announcement/{id}")
    public @ResponseBody String getLevel(@PathVariable("id") String id) {
        Map<String, Object> stringObjectMap = rp.GetObject2(id, rp.processorConfig.getRoot(), true, true);
        return rp.gson.toJson(stringObjectMap);
    }


    @PostMapping("/config/{type}")
    public @ResponseBody String getConfig(@PathVariable("type") String type, @RequestBody String body) {
        Map<String, Object> obj;
        switch (type){
            case "xml":
               obj = U.fromXmlMap(body);
               break;
            case "json":
                obj = new Gson().fromJson(body, Map.class);
                break;
            default:
                return "unknown type";
        }
        StructureEntry se = new StructureEntry();
        se.setType(ProcessorConfig.STRUCTURE_TYPE_MAP);
        rp.getRootStructureFromMap(obj, se);
        Map<String, Object> output = new HashMap<>();
        output.put("root", se);
        return new Gson().toJson(output);
    }

    @PostMapping("/announcement/xml/{id}")
    public @ResponseBody String addXMLAnnouncement(@PathVariable("id") String id,@RequestBody String xml) {
        Map<String, Object> obj = U.fromXmlMap(xml);
        rp.PopulatePayload(id, obj);
        return "OK";
    }

    @PostMapping("/announcement/json/{id}")
    public @ResponseBody String addJSONAnnouncement(@PathVariable("id") String id ,@RequestBody String body) {
        Map<String,Object> obj = new Gson().fromJson(body, Map.class);
        rp.PopulatePayload(id, obj);
        return "OK";
    }

    @RequestMapping(value = "/func/{id}/{fid}", method=RequestMethod.GET)
    public @ResponseBody String processFunc2(@PathVariable(name = "id") String id,@PathVariable(name = "fid") String fid) {
        String output;
        try {
            output = rp.processFunction(id, fid);
        } catch (Exception e) {
            output = "";
        }
        return output;
    }


    @GetMapping("/foos/{id}")
    public @ResponseBody String getFooById(@PathVariable String id) {
        return "ID: " + id;
    }

    @RequestMapping("/")
    public String index() {
        System.out.println(rp);
        return "index";
    }

    @PostMapping("/keys")
    public @ResponseBody Object keys() {
        //return redisRepository.findAllMovies();
        return "";
    }

    @RequestMapping("/values")
    public @ResponseBody String findAll() {
//        Map<Object, Object> aa = redisRepository.findAllMovies();
//        Map<String, String> map = new HashMap<String, String>();
//        for (Map.Entry<Object, Object> entry : aa.entrySet()) {
//            String key = (String) entry.getKey();
//            map.put(key, aa.get(key).toString());
//        }
//        return map;
        return "";
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public ResponseEntity<String> add(
            @RequestParam String key,
            @RequestParam String value) {

        //Movie movie = new Movie(key, value);

        //redisRepository.add(movie);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public ResponseEntity<String> delete(@RequestParam String key) {
        //redisRepository.delete(key);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}