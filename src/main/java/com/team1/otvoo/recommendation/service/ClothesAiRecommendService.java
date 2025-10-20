package com.team1.otvoo.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.otvoo.clothes.dto.OotdDto;
import com.team1.otvoo.clothes.entity.Clothes;
import com.team1.otvoo.clothes.entity.ClothesImage;
import com.team1.otvoo.clothes.entity.ClothesType;
import com.team1.otvoo.clothes.mapper.ClothesMapper;
import com.team1.otvoo.clothes.repository.ClothesImageRepository;
import com.team1.otvoo.clothes.repository.ClothesRepository;
import com.team1.otvoo.exception.ErrorCode;
import com.team1.otvoo.exception.RestException;
import com.team1.otvoo.recommendation.client.OpenAiClient;
import com.team1.otvoo.recommendation.dto.ClothesAiDto;
import com.team1.otvoo.recommendation.dto.ClothesFilterWrapperDto;
import com.team1.otvoo.recommendation.dto.FilteredClothesResponse;
import com.team1.otvoo.recommendation.dto.RecommendationDto;
import com.team1.otvoo.recommendation.entity.ClothesAiAttributes;
import com.team1.otvoo.recommendation.entity.Recommendation;
import com.team1.otvoo.recommendation.entity.RecommendationClothes;
import com.team1.otvoo.recommendation.repository.ClothesAiAttributesRepository;
import com.team1.otvoo.recommendation.repository.RecommendationRepository;
import com.team1.otvoo.security.CustomUserDetails;
import com.team1.otvoo.storage.S3ImageStorage;
import com.team1.otvoo.user.dto.ProfileDto;
import com.team1.otvoo.user.entity.Profile;
import com.team1.otvoo.user.entity.User;
import com.team1.otvoo.user.mapper.ProfileMapper;
import com.team1.otvoo.user.repository.ProfileRepository;
import com.team1.otvoo.weather.dto.WeatherDto;
import com.team1.otvoo.weather.entity.WeatherForecast;
import com.team1.otvoo.weather.mapper.WeatherMapper;
import com.team1.otvoo.weather.repository.WeatherForecastRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClothesAiRecommendService {
  private final ClothesRepository clothesRepository;
  private final ClothesAiAttributesRepository clothesAiAttributesRepository;
  private final WeatherForecastRepository weatherForecastRepository;
  private final ProfileRepository profileRepository;
  private final RecommendationRepository recommendationRepository;
  private final ClothesImageRepository clothesImageRepository;
  private final S3ImageStorage s3ImageStorage;
  private final WeatherMapper weatherMapper;
  private final ProfileMapper profileMapper;
  private final ClothesMapper clothesMapper;
  private final ObjectMapper objectMapper;
  private final OpenAiClient openAiClient;

  @Transactional
  public RecommendationDto filterAndRecommendClothes(UUID weatherId) {
    WeatherForecast weatherForecast = weatherForecastRepository.findByIdFetch(weatherId)
        .orElseThrow(() -> new RestException(ErrorCode.WEATHER_FORECAST_NOT_FOUND,
            Map.of("weatherId", weatherId)));

    User user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
    UUID userId = user.getId();

    Profile profile = profileRepository.findByUserId(userId).orElseThrow(
        () -> new RestException(ErrorCode.PROFILE_NOT_FOUND, Map.of("userId", userId))
    );

    ProfileDto profileDto = profileMapper.toProfileDto(userId, profile, "image.url");

    // 온도 -> 계절 매핑
    String baseSeason;
    double temperature = weatherForecast.getTemperature().getCurrent();
    if (temperature <= 7) {
      baseSeason = "겨울";
    } else if (temperature <= 22) {
      baseSeason = "봄/가을";   // 묶어서 관리
    } else {
      baseSeason = "여름";
    }

    // 계절에 따라 필터링할 value List 추출
    List<String> allowedSeasons;
    if ("봄/가을".equals(baseSeason)) {
      allowedSeasons = List.of("봄", "가을");
    } else if ("여름".equals(baseSeason)) {
      allowedSeasons = List.of("여름");
    } else {
      allowedSeasons = List.of("겨울");
    }

    List<Clothes> clothesList = clothesRepository.findByUserIdAndSeasons(userId, allowedSeasons);

    List<ClothesAiAttributes> clothesAiAttributesList =
        clothesAiAttributesRepository.findByUserIdClothes_TypeInFetch(userId);

    // 2-1. 날씨 Entity -> WeatherDto 변환
    WeatherDto weatherDto = weatherMapper.toDto(weatherForecast);

    // 2-2. AI 속성을 빠르게 찾기 위해 Map으로 변환
    Map<UUID, Map<String, String>> aiAttributesMap = clothesAiAttributesList.stream()
        .collect(Collectors.toMap(
            aiAttr -> aiAttr.getClothes().getId(),
            ClothesAiAttributes::getAttributes
        ));

    // 2-3. 옷 Entity List -> List<ClothesAiDto> 변환
    List<ClothesAiDto> clothesAiDtos = clothesList.stream().map(clothes -> {
      List<String> combinedAttributes = new ArrayList<>();

      // 사용자 선택 속성 추가 -> 코드 변환
      clothes.getSelectedValues().forEach(sv ->
          combinedAttributes.add(toAttrValueCode(sv.getValue().getValue()))
      );
      // AI 추출 속성 추가
      Map<String, String> aiAttrs = aiAttributesMap.get(clothes.getId());
      return new ClothesAiDto(clothes.getId(), toTypeCode(clothes.getType()),
          combinedAttributes, null);
    }).toList();

    // 2-4. 최종 요청 DTO 생성
    ClothesFilterWrapperDto requestPayload = new ClothesFilterWrapperDto(profileDto, weatherDto,
        clothesAiDtos);

    // 2-5. DTO를 JSON 문자열로 변환
    String jsonPayload;
    try {
      jsonPayload = objectMapper.writeValueAsString(requestPayload);

    } catch (JsonProcessingException e) {
      log.error("LLM 요청 DTO를 JSON으로 변환하는 중 에러 발생", e);
      throw new RestException(ErrorCode.JSON_PARSE_ERROR);
    }

    // --- 3. LLM 호출 및 결과 처리 ---
    FilteredClothesResponse responseDto = null;
    try {
      // 3-1. OpenAiClient 호출하여 DTO를 직접 받음
      responseDto = openAiClient.filterClothes(jsonPayload);
    } catch (Exception e) {
      log.error("LLM 호출 또는 응답 처리 중 에러 발생", e);
      throw new RestException(ErrorCode.LLM_PROCESSING_ERROR);
    }

    // 3-2. 기존 저장된 추천 정보 삭제
    recommendationRepository.deleteAllByWeather_Id(weatherId);
    recommendationRepository.flush();

    // 4. 추천 결과 DB에 저장
    Recommendation savedRecommendation = saveRecommendation(user, weatherForecast, responseDto);

    List<OotdDto> ootds = savedRecommendation.getClothes().stream()
        .map(rc -> {
          Clothes clothes = rc.getClothes();
          ClothesImage image = clothesImageRepository.findByClothes_Id(clothes.getId()).orElse(null);
          String url = (image != null)
              ? s3ImageStorage.getPresignedUrl(image.getImageKey(), image.getContentType())
              : null;
          return clothesMapper.toOotdDto(clothes, url);
        })
        .toList();


    return new RecommendationDto(savedRecommendation.getWeather().getId(),
        savedRecommendation.getUser().getId(),
        ootds);
  }

  // 추천 결과를 저장하는 헬퍼 메서드
  private Recommendation saveRecommendation(User user, WeatherForecast weatherForecast,
      FilteredClothesResponse response) {

    // 1. Recommendation 엔티티 생성
    Recommendation recommendation = new Recommendation(user, weatherForecast);

    // 2. 추천받은 옷의 ID로 Clothes 엔티티 조회
    List<UUID> recommendedClothesIds = response.clothesIds();
    List<Clothes> recommendedClothesList = clothesRepository.findAllById(recommendedClothesIds);

    //  중복 제거: 같은 Type이 여러 개 있으면 첫 번째만 남기고 나머지는 제거
    Map<String, Clothes> uniqueByType = new LinkedHashMap<>();
    for (Clothes clothes : recommendedClothesList) {
      String type = clothes.getType().name();
      if (!uniqueByType.containsKey(type)) {
        uniqueByType.put(type, clothes); // 첫 등장한 Type만 저장
      } else {
        log.warn("중복된 Type 발견: {} → 첫 번째만 유지", type);
      }
    }

    // 3. 조회된 Clothes 엔티티를 RecommendationClothes에 추가
    for (Clothes clothes : uniqueByType.values()) {
      RecommendationClothes recommendationClothes = new RecommendationClothes(clothes);
      recommendation.addClothes(recommendationClothes);
    }

    Recommendation savedRecommendation = recommendationRepository.save(recommendation);
    log.info("새로운 추천 저장완료. Recommendation ID: {}", recommendation.getId());

    return savedRecommendation;
  }

  private String toTypeCode(ClothesType type) {
    return switch (type) {
      case TOP -> "T0";
      case BOTTOM -> "T1";
      case DRESS -> "T2";
      case OUTER -> "T3";
      case UNDERWEAR -> "T4";
      case ACCESSORY -> "T5";
      case SHOES -> "T6";
      case SOCKS -> "T7";
      case HAT -> "T8";
      case BAG -> "T9";
      case SCARF -> "T10";
      case ETC -> "T11";
    };
  }

  // 속성 Definition + Value 코드 변환
  private String toAttrValueCode(String value) {
    return switch (value) {
      // 계절
      case "봄" -> "S0";
      case "여름" -> "S1";
      case "가을" -> "S2";
      case "겨울" -> "S3";

      // 방수
      case "가능" -> "WP1";
      case "불가능" -> "WP0";

      // 방풍
      case "뛰어남" -> "WF2";
      case "중간" -> "WF1";
      case "방풍없음" -> "WF0";

      // 색상
      case "빨강" -> "C0";
      case "노랑" -> "C1";
      case "파랑" -> "C2";
      case "검정" -> "C3";
      case "흰색" -> "C4";

      // 안감
      case "부드러움" -> "L0";
      case "까칠함" -> "L1";
      case "따뜻함" -> "L2";

      // 두께감
      case "얇음" -> "TH0";
      case "약간두꺼움" -> "TH1";
      case "두꺼움" -> "TH2";

      // 스타일
      case "포멀" -> "ST0";
      case "캐주얼" -> "ST1";
      case "스트릿" -> "ST2";
      case "아웃도어" -> "ST3";
      case "스포츠" -> "ST4";

      // 비침정도
      case "비침없음" -> "TR0";
      case "살짝비침" -> "TR1";

      // 소매길이
      case "민소매" -> "SL0";
      case "반소매" -> "SL1";
      case "7부" -> "SL2";
      case "긴소매" -> "SL3";

      // 쿨링소재
      case "쿨링있음" -> "CL1";
      case "쿨링없음" -> "CL0";

      default -> "X"; // 정의 안 된 값
    };
  }
}
