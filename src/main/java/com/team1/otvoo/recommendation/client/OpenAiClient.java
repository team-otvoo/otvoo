package com.team1.otvoo.recommendation.client;

import com.team1.otvoo.exception.ErrorCode;
import com.team1.otvoo.exception.RestException;
import com.team1.otvoo.recommendation.dto.FilteredClothesResponse;
import com.team1.otvoo.recommendation.dto.VisionAttributeResponseDto;
import java.net.MalformedURLException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

@RequiredArgsConstructor
@Component
@Slf4j
public class OpenAiClient {

  private final ChatClient chatClient;

  public VisionAttributeResponseDto analyzeImage(String imageUrl) {
    BeanOutputConverter<VisionAttributeResponseDto> parser = new BeanOutputConverter<>(
        VisionAttributeResponseDto.class);

    String response = chatClient.prompt()
        .user(userSpec -> {
          try {
            userSpec.text(
                    """
                        <role>
                        의류 사진을 주면, 제공된 format에 맞춰 응답해야 한다.
                        </role>
                        <instruction>
                        1. 예시 속성과 다른 속성도 폭넓게 가져와야만 한다.
                        2. 속성은 반드시 날씨와 관련된 속성으로만 가져와야 한다.
                        2-1. 날씨와 관련된 속성이란, 덥고 추울 때 / 비올 때나 맑을 때 선호도가 달라지는 의상 속성을 이야기하는 것.
                        2-2. uvProtection, dryTime과 같은 너무 특이한 속성이 아닌, 보편적인 속성을 가져올 것.
                        3. 계절(season), 두께(thickness), 소매 길이(sleevsLength), 방수(waterProof) 속성은 제외해야한다.
                        4. 속성 개수는 5개로 제한한다.
                        5. 색상,핏감과 같은 날씨와 상관없는 속성은 제외하고 가져와야 한다.
                        </instruction>
                        <example>
                        예시 응답을 알려주겠다. 예시 응답과 같은 의류가 나오더라도 이 예시 값을 똑같이 활용하지는 마라.
                        {
                            "AiResponse": [
                                {
                                    "color": "파란색",
                                    "sleevesLength": "짧음",
                                    "neck": "카라있음",
                                    "width": "500",
                                    "length": "300"
                                },
                            ]
                        }
                        </example>
                        format은 아래와 같다.
                        """ + parser.getFormat())
                .media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(imageUrl));
          } catch (MalformedURLException e) {
            log.warn("URL 형식이 올바르지 않습니다 - url: {}", imageUrl);
            throw new RestException(ErrorCode.MALFORMED_URL, Map.of("url", imageUrl));
          }
        })
        .call()
        .content();

    return parser.convert(response);
  }

  public FilteredClothesResponse filterClothes(String data) {
    var parser = new BeanOutputConverter<>(FilteredClothesResponse.class);

    // 프롬프트 + 데이터 합체
    String prompt = """
                <role>
                너는 사용자의 프로필, 날씨, 그리고 옷장 데이터를 종합하여 개인화된 옷차림을 추천하는 세계 최고의 AI 패션 스타일리스트다.
                </role>
        
                <input_data>
                %s
                </input_data>
        
                <instruction>
                너의 임무는 입력받은 'input_data'를 바탕으로 아래 규칙을 철저히 지켜 최종 추천 코디 하나를 완성하는 것이다.
        
                ### 절대 규칙 (최우선)
                - 추천 결과는 반드시 'clothesAiDtos' 목록에서 선택된 옷으로만 구성한다.
                - 모든 Type(TOP, BOTTOM, DRESS, OUTER, UNDERWEAR, ACCESSORY, SHOES, SOCKS, HAT, BAG, SCARF, ETC)은 **정확히 하나씩만 포함**되어야 한다.
                - 동일한 Type 에서 두 개 이상의 옷을 추천하는 것은 절대 금지다.
                - DRESS는 (TOP, BOTTOM)을 대체하는 항목이다.
                - 따라서 결과에는 "TOP+BOTTOM" 또는 "DRESS" 중 하나만 있어야 한다. (동시에 존재할 수 없음)
                - 즉, TOP과 DRESS가 함께 선택되는 경우는 절대 불가능하다.
        
                ### 1단계: 날씨와 프로필 분석
                - 'weatherDto'와 'profileDto' 정보를 분석하여 추천의 기준으로 삼는다.
        
                ### 2단계: 핵심 코디 조합
                - TOP + BOTTOM 조합은 반드시 한 쌍만 선택할 수 있다. (예: TOP 1개 + BOTTOM 1개)
                - DRESS는 하나만 선택 가능하다.
                - 성별 규칙:
                  - male: DRESS는 추천 불가.
                  - female: DRESS 또는 TOP+BOTTOM 중 하나만 선택해야 하며, 한쪽으로 편향되지 않도록 다양성을 유지한다.
                - 계절 규칙:
                  - TOP, BOTTOM, SHOES, HAT, DRESS, OUTER는 현재 계절에 맞는 옷만 선택한다.
                  - OUTER는 봄/가을에는 얇은 아우터, 겨울에는 두꺼운 아우터만 선택한다.
                - 온도 민감도 규칙:
                  - 0~2 (추위를 덜 탐): 여름이라도 냉방 환경을 고려해 '두꺼움' 속성을 적극 활용. 겨울에는 무조건 '두꺼움'.
                  - 3~5 (더위를 탐): 여름에는 '얇음'만, 겨울에는 '얇음'과 '두꺼움' 모두 가능.
        
                ### 3단계: 서브 코디 매칭
                - 핵심 코디에 어울리는 UNDERWEAR, ACCESSORY, SOCKS, BAG, SCARF, ETC를 각각 하나씩 선택한다.
                - 모든 Type은 중복 없이 정확히 하나씩 포함되어야 한다.
        
                ### 4단계: 최종 결과물 생성
                - 선택된 모든 옷의 ID를 'clothesIds' 목록에 담는다.
                - 응답은 반드시 아래 JSON 형식을 따라야 하며, 그 외 불필요한 설명은 포함하지 않는다.
               </instruction>
        
                <format>
                %s
                </format>
        """.formatted(data, parser.getFormat());

    // API 호출
    long start = System.currentTimeMillis();
    CallResponseSpec response = chatClient.prompt()
        .user(userSpec -> userSpec.text(prompt))
        .call();
    String stringResponse = response.content();
    Usage usage = response.chatResponse().getMetadata().getUsage();
    long end = System.currentTimeMillis();

    log.info("응답시간: {}ms", end - start);
    log.info("입력토큰: {}, 출력토큰: {}, 전체토큰: {}, 비용: {}$", usage.getPromptTokens(), usage.getGenerationTokens(), usage.getTotalTokens(), usage.getPromptTokens() * 0.00000015 + usage.getGenerationTokens() * 0.00000060);

    return parser.convert(stringResponse);
  }
}
