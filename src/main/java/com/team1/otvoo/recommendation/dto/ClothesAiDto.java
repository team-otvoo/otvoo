package com.team1.otvoo.recommendation.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClothesAiDto {
    UUID id;
    String type;
    List<String> attributes;
    Map<String, String> aiAttributes;
}
