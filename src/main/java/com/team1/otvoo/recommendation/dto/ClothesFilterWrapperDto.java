package com.team1.otvoo.recommendation.dto;

import com.team1.otvoo.user.dto.ProfileDto;
import com.team1.otvoo.weather.dto.WeatherDto;
import java.util.List;

public record ClothesFilterWrapperDto(
    ProfileDto profileDto,
    WeatherDto weatherDto,
    List<ClothesAiDto> clothesAiDto
) {

}
