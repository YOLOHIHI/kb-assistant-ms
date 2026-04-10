package com.codec.kb.gateway;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({GatewayConfig.class, SecurityProps.class, CryptoProps.class})
public class AppBeans {
  private static final String DEV_INTERNAL_TOKEN = "dev-internal-token";
  private static final String DEV_BOOTSTRAP_ADMIN_PASSWORD = "admin123";
  private static final String DEV_MASTER_KEY = "dev-master-key-change-me";

  @Bean
  @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "false", matchIfMissing = true)
  RestClient.Builder plainRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  @ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true")
  @LoadBalanced
  RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  @Primary
  RestClient restClient(RestClient.Builder builder) {
    return builder.build();
  }

  @Bean
  RestClient healthRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(2));
    return RestClient.builder().requestFactory(factory).build();
  }

  @Bean
  StartupSafetyChecks startupSafetyChecks(
      GatewayConfig gatewayConfig,
      SecurityProps securityProps,
      CryptoProps cryptoProps) {
    return new StartupSafetyChecks(gatewayConfig, securityProps, cryptoProps);
  }

  static final class StartupSafetyChecks {
    StartupSafetyChecks(GatewayConfig gatewayConfig, SecurityProps securityProps, CryptoProps cryptoProps) {
      if (gatewayConfig != null && gatewayConfig.allowInsecureDefaults()) {
        return;
      }
      requireConfigured("KB_INTERNAL_TOKEN", gatewayConfig == null ? null : gatewayConfig.internalToken(), DEV_INTERNAL_TOKEN);
      requireConfigured("KB_BOOTSTRAP_ADMIN_USER", securityProps == null ? null : securityProps.bootstrapAdminUser(), null);
      requireConfigured(
          "KB_BOOTSTRAP_ADMIN_PASSWORD",
          securityProps == null ? null : securityProps.bootstrapAdminPassword(),
          DEV_BOOTSTRAP_ADMIN_PASSWORD);
      requireConfigured("KB_CRYPTO_MASTER_KEY", cryptoProps == null ? null : cryptoProps.masterKey(), DEV_MASTER_KEY);
    }

    private static void requireConfigured(String envName, String value, String forbiddenDefault) {
      String normalized = value == null ? "" : value.trim();
      if (normalized.isBlank()) {
        throw new IllegalStateException(envName + " must be configured or explicitly opt into local dev defaults");
      }
      if (forbiddenDefault != null && forbiddenDefault.equals(normalized)) {
        throw new IllegalStateException(
            envName + " is using an insecure development default; set KB_ALLOW_INSECURE_DEFAULTS=true only for local development");
      }
    }
  }
}
