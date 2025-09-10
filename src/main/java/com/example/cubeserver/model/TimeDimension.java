package com.example.cubeserver.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeDimension {
    private String dimension;
    private String granularity;
    private Object dateRange; // List<String> or String
}
