package com.codec.kb.ai;

import com.codec.kb.common.security.InternalAuthAutoConfig;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({AiServiceConfig.class, SiliconflowConfig.class})
@Import(InternalAuthAutoConfig.class)
public class Beans {
  @Bean
  @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false", matchIfMissing = true)
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true")
  @LoadBalanced
  RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }
}
