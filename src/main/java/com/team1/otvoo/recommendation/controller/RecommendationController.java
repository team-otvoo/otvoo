package com.team1.otvoo.recommendation.controller;

import com.team1.otvoo.recommendation.dto.RecommendationDto;
import com.team1.otvoo.recommendation.service.RecommendationService;
import com.team1.otvoo.security.CustomUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

  private final RecommendationService recommendationService;

  @GetMapping
  public ResponseEntity<RecommendationDto> getRecommendation(
    @RequestParam("weatherId") UUID weatherId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {

    log.info("의상 추천 요청: weatherId={}", weatherId);

    UUID userId = customUserDetails.getUser().getId();
    RecommendationDto recommendationDto = recommendationService.get(weatherId, userId);

    log.info("의상 추천 완료: weatherId={}", weatherId);

    return ResponseEntity
        .ok(recommendationDto);
  }

  @PostMapping("/refresh")
  public ResponseEntity<RecommendationDto> refreshRecommendation(
      @RequestParam("weatherId") UUID weatherId,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    log.info("의상 재추천 요청: weatherId={}", weatherId);

    UUID userId = customUserDetails.getUser().getId();
    RecommendationDto recommendationDto = recommendationService.refresh(weatherId, userId);

    log.info("의상 재추천 완료: weatherId={}", weatherId);
    return ResponseEntity
        .ok(recommendationDto);
  }
}
