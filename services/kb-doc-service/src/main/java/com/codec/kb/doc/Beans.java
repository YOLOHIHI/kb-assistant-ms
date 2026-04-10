package com.codec.kb.doc;

import com.codec.kb.common.security.InternalAuthAutoConfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DocServiceConfig.class)
@Import(InternalAuthAutoConfig.class)
public class Beans {
  @Bean
  RestClient restClient() {
    return RestClient.builder().build();
  }
}
