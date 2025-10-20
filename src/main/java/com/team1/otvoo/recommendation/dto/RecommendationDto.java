package com.team1.otvoo.recommendation.dto;

import com.team1.otvoo.clothes.dto.OotdDto;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RecommendationDto{
    UUID weatherId;
    UUID userId;
    List<OotdDto> clothes;
}
