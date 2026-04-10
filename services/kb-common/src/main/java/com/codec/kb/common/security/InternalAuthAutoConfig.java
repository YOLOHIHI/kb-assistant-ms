package com.codec.kb.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "kb.internal-token")
public class InternalAuthAutoConfig {
  @Bean
  InternalAuthFilter internalAuthFilter(@Value("${kb.internal-token}") String token) {
    return new InternalAuthFilter(token);
  }
}
