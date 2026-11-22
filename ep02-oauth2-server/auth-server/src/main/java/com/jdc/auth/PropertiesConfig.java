package com.jdc.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.jdc.auth.props.MailConfigProps;
import com.jdc.auth.props.OtpCodeProps;

@Configuration
@EnableConfigurationProperties({MailConfigProps.class, OtpCodeProps.class})
public class PropertiesConfig {
}
