package com.team1.otvoo.recommendation.service;

import com.team1.otvoo.recommendation.dto.RecommendationDto;
import java.util.UUID;

public interface RecommendationService {
  RecommendationDto refresh(UUID weatherId, UUID userId);
  RecommendationDto get(UUID weatherId, UUID userId);
}
