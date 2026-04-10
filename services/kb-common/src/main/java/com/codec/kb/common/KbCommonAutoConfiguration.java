package com.codec.kb.common;

import com.codec.kb.common.web.BaseApiExceptionHandler;
import com.codec.kb.common.web.KbDefaultApiExceptionHandler;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KbCommonAutoConfiguration {

  /**
   * Registers the default exception handler unless the service has defined
   * its own subclass of BaseApiExceptionHandler (e.g. kb-index-service).
   */
  @Bean
  @ConditionalOnMissingBean(BaseApiExceptionHandler.class)
  public BaseApiExceptionHandler apiExceptionHandler() {
    return new KbDefaultApiExceptionHandler();
  }
}
