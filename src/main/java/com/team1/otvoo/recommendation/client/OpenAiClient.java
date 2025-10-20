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
        Role: 세계 최고의 AI 패션 스타일리스트
        Task: input_data를 기반으로 개인화된 옷차림을 추천한다.
        
        [input_data]
        %s
        [/input_data]
        
        규칙:
        1. clothesAiDtos에서만 선택, 각 Type(T0~T11) 정확히 1개씩.
           - T2는 T0+T1을 대체, 중복 불가.
        2. weatherDto + profileDto.sensitivity 반영:
           - sensitivity 0~2(추위 민감):
             - S3→TH2 필수 
             - S1→TH1/TH2 허용 
             - S1에도 T3 가능
           - sensitivity 3~5(더위 민감): 
             - S1→TH0만 
             - S1에서 T3 금지 
             - S3→TH0~TH2 가능
        3. 서브 코디: T4,T5,T7,T9,T10,T11 각 1개 필수.
        4. aiAttributes는 참고만, 선택 근거는 attributes 코드 기준.
        5. 최종 응답은 clothesIds 배열(JSON 형식)만 출력.
        
        ### attributes (압축 코드 예시)
        - S0=봄, S1=여름, S2=가을, S3=겨울  
        - WP0=불가능, WP1=가능  
        - WF0=방풍없음, WF1=중간, WF2=뛰어남  
        - C0=빨강, C1=노랑, C2=파랑, C3=검정, C4=흰색  
        - TH0=얇음, TH1=약간두꺼움, TH2=두꺼움  
        - ST0=포멀, ST1=캐주얼, ST2=스트릿, ST3=아웃도어, ST4=스포츠  
        - SL0=민소매, SL1=반소매, SL2=7부, SL3=긴소매
        - TR0=비침없음, TR1=살짝비침
        - CL0=쿨링없음, CL1=쿨링있음
        - L0=부드러움, L1=까칠함, L2=따뜻함
        
        Format:
        %s
        """.formatted(data, parser.getFormat());

    // API 호출
    long start = System.currentTimeMillis();
    CallResponseSpec response = chatClient.prompt()
        .user(userSpec -> userSpec.text(prompt))
        .call();
    String stringResponse = response.content();
    Usage usage = response.chatResponse().getMetadata().getUsage();
    long end = System.currentTimeMillis();
    double cost = usage.getPromptTokens() * 0.00000015
        + usage.getGenerationTokens() * 0.00000060;

    log.info("응답시간: {}ms", end - start);
    log.info("입력토큰: {}, 출력토큰: {}, 전체토큰: {}, 비용: {}$",
        usage.getPromptTokens(),
        usage.getGenerationTokens(),
        usage.getTotalTokens(),
        String.format("%.6f", cost));

    return parser.convert(stringResponse);
  }
}
