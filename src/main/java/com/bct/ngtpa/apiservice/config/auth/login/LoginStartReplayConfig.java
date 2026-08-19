package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay.*;
import com.bct.ngtpa.apiservice.adapter.out.security.login.config.LoginPreAuthContextProtectionProperties;
import com.bct.ngtpa.apiservice.adapter.out.security.login.replay.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class LoginStartReplayConfig {
  @Bean
  LoginStartReplayProtectorPort loginStartReplayProtector(
      ObjectMapper mapper, LoginPreAuthContextProtectionProperties properties) {
    return new AesGcmLoginStartReplayProtector(
        mapper, properties.getKeyVersion(), properties.getSecret());
  }

  @Bean
  LoginStartReplayCommitRefGenerator loginStartReplayCommitRefGenerator() {
    return new SecureLoginStartReplayCommitRefGenerator();
  }

  @Bean
  RedisLoginStartReplayKeyFactory redisLoginStartReplayKeyFactory(
      @Value("${redis-cache.key-prefix:ngtpa}") String prefix) {
    return new RedisLoginStartReplayKeyFactory(prefix);
  }

  @Bean
  LoginStartReplayPort loginStartReplayPort(
      RedisLoginStartReplayKeyFactory keys,
      ReactiveStringRedisTemplate redis,
      ObjectMapper mapper) {
    return new RedisLoginStartReplayAdapter(keys, redis, mapper);
  }
}
