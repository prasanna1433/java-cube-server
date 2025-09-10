package com.example.cubeserver.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Filter {
    private String member;
    private String operator;
    private List<String> values;
}
