package com.example.cubeserver.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Query {
    private List<String> measures;
    private List<String> dimensions;
    private List<TimeDimension> timeDimensions;
    private List<Filter> filters;
    private Integer limit = 500;
    private Integer offset = 0;
    private Map<String, String> order;
    private Boolean ungrouped = false;
}

/*
{"query":{
  "filters": [
    {
      "member": "Measures.country",
      "operator": "equals",
      "values": [
        "Germany"
      ]
    }
  ],
  "measures": [
    "Measures.confirmed_cases"
  ],
  "dimensions": [
    "Measures.date"
  ]
}}
 */
