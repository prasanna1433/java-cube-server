package com.example.cubeserver.controller;

import com.example.cubeserver.model.Query;
import com.example.cubeserver.service.CubeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Service;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CubeController {
    private final CubeClient cubeClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> resourceStore = new ConcurrentHashMap<>();
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(CubeController.class);

    @Autowired
    public CubeController(CubeClient cubeClient) {
        this.cubeClient = cubeClient;
    }

    /**
     * Converts the given data object to a YAML string using SnakeYAML.
     * Used internally for formatting output.
     * @param data The data object to convert.
     * @return YAML string representation of the data.
     */
    private String dataToYaml(Object data) {
        Yaml yaml = new Yaml();
        return yaml.dumpAsMap(data);
    }

    /**
     * Retrieves a description of the available data from Cube.js, including cubes, dimensions, and measures.
     * Used internally and by describeData().
     * @return A formatted string describing the data schema.
     */
    private String dataDescription() {
        Map<String, Object> meta = cubeClient.describe().block();
        if (meta == null || meta.containsKey("error")) {
            String error = meta != null ? String.valueOf(meta.get("error")) : "Unknown error";
            logger.error("Error in data_description: {}", error);
            return "Error: Description of the data is not available: " + error + ", " + meta;
        }
        List<Object> description = new ArrayList<>();
        List<Map<String, Object>> cubes = (List<Map<String, Object>>) meta.get("cubes");
        if (cubes != null) {
            for (Map<String, Object> cube : cubes) {
                Map<String, Object> desc = new HashMap<>();
                desc.put("name", cube.get("name"));
                desc.put("title", cube.get("title"));
                desc.put("description", cube.get("description"));
                List<Map<String, Object>> dims = (List<Map<String, Object>>) cube.get("dimensions");
                List<Map<String, Object>> dimList = new ArrayList<>();
                if (dims != null) {
                    for (Map<String, Object> dim : dims) {
                        Map<String, Object> d = new HashMap<>();
                        d.put("name", dim.get("name"));
                        d.put("title", dim.getOrDefault("shortTitle", dim.get("title")));
                        d.put("description", dim.get("description"));
                        dimList.add(d);
                    }
                }
                desc.put("dimensions", dimList);
                List<Map<String, Object>> measures = (List<Map<String, Object>>) cube.get("measures");
                List<Map<String, Object>> measureList = new ArrayList<>();
                if (measures != null) {
                    for (Map<String, Object> measure : measures) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", measure.get("name"));
                        m.put("title", measure.getOrDefault("shortTitle", measure.get("title")));
                        m.put("description", measure.get("description"));
                        measureList.add(m);
                    }
                }
                desc.put("measures", measureList);
                description.add(desc);
            }
        }
        return "Here is a description of the data available via the read_data tool:\n\n" + dataToYaml(description);
    }

    /**
     * Returns the schemas available in the Cube.js instance as a text description.
     * This is exposed as an AI tool.
     * @return Map containing the schema description as text.
     */
    @Tool(description = "Get the schemas available in the Cube.js instance")
    public Map<String, Object> getCubeJsMeta() {
        return describeData();
    }

    /**
     * Returns a description of the data schema available in Cube.js.
     * Used by getCubeJsMeta() and for external schema queries.
     * @return Map containing the schema description as text.
     */
    public Map<String, Object> describeData() {
        String text = dataDescription();
        return Map.of("type", "text", "text", text);
    }

    /**
     * Retrieves data from Cube.js based on the provided query.
     * This is exposed as an AI tool.
     * @param query The query object specifying measures, dimensions, filters, etc.
     * @return The result of the readData method (data and resource info).
     */
    @Tool(description = "Get the data from Cube.js based on the provided query")
    public Object getDataFromCubeJs(Query query) {
        return readData(query);
    }

    /**
     * Reads data from Cube.js using the provided query.
     * Converts the query to the correct format, sends it to Cube.js, and returns the result.
     * @param query The query object specifying measures, dimensions, filters, etc.
     * @return List containing a text (YAML) and a resource (JSON) representation of the data, or an error map.
     */
    public Object readData(Query query) {
        try {
            Map<String, Object> queryMap = objectMapper.convertValue(query, Map.class);
            // Ensure measures, dimensions, timeDimensions are lists of strings
            queryMap.computeIfPresent("measures", (k, v) -> ((List<?>) v).stream().map(Object::toString).collect(Collectors.toList()));
            queryMap.computeIfPresent("dimensions", (k, v) -> ((List<?>) v).stream().map(Object::toString).collect(Collectors.toList()));
            queryMap.computeIfPresent("timeDimensions", (k, v) -> ((List<?>) v).stream().map(Object::toString).collect(Collectors.toList()));
            logger.info("read_data called with query: {}", queryMap);
            Map<String, Object> response = cubeClient.query(queryMap).block();
            if (response == null || response.containsKey("error")) {
                String error = response != null ? String.valueOf(response.get("error")) : "Unknown error";
                logger.error("Error in read_data: {}", error);
                return Map.of("error", error);
            }
            List<Object> data = (List<Object>) response.getOrDefault("data", List.of());
            String dataId = UUID.randomUUID().toString();
            resourceStore.put(dataId, data);
            logger.info("Added results as resource with ID: {}", dataId);
            Map<String, Object> output = Map.of(
                    "type", "data",
                    "data_id", dataId,
                    "data", data
            );
            String yamlOutput = dataToYaml(output);
            String jsonOutput = objectMapper.writeValueAsString(output);
            return List.of(
                Map.of("type", "text", "text", yamlOutput),
                Map.of(
                    "type", "resource",
                    "resource", Map.of(
                        "uri", "data://" + dataId,
                        "text", jsonOutput,
                        "mimeType", "application/json"
                    )
                )
            );
        } catch (Exception e) {
            logger.error("Error in read_data: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Retrieves a stored resource by its dataId.
     * @param dataId The unique identifier for the stored data resource.
     * @return The stored data object, or an error map if not found.
     */
    public Object getResource(String dataId) {
        Object data = resourceStore.get(dataId);
        if (data == null) {
            return Map.of("error", "Resource not found");
        }
        return data;
    }

   /* public static void main(String[] args) {
        CubeController client = new CubeController(cubeClient);
        System.out.println(client.readData(47.6062, -122.3321));
        System.out.println(client.getAlerts("NY"));
    }*/
}
