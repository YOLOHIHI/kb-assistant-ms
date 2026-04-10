package com.codec.kb.index;

import com.codec.kb.common.security.InternalAuthAutoConfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties({IndexServiceConfig.class, IndexTuning.class})
@Import(InternalAuthAutoConfig.class)
public class Beans {
}
