package com.team1.otvoo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@EnableCaching
@Configuration
public class CacheConfig {

  //캐싱 처리를 위한 빈 등록
  @Bean
  public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
    // 직렬화 시 class type 정보를 포함시키기 위함 -> type 정보가 있어야 역직렬화 시 ClassCastException가 발생하지 않음
    // 기존 GenericJackson2JsonRedisSerializer는 타입 정보를 포함하지만, 커스텀 ObjectMapper를 사용할 경우 포함 X
    BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.team1.otvoo") // 해당 패키지 하위만 허용
        .allowIfSubType("java.util") // Collection을 역직렬화할 경우 필요
        .allowIfSubType("java.util.ImmutableCollections")
        .build();

    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .activateDefaultTyping(
            typeValidator,
            DefaultTyping.NON_FINAL // 또는 OBJECT_AND_NON_CONCRETE 등 필요에 따라 선택
        )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


    // 직렬화기 생성
    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);

    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
        )
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(serializer)
        )
        .entryTtl(Duration.ofSeconds(180))
        .disableCachingNullValues();

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(config)
        .build();
  }
}
