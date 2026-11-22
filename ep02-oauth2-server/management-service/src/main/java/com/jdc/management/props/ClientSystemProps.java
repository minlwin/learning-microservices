package com.jdc.management.props;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.client")
public class ClientSystemProps {

	private List<@NotBlank String> systems = List.of();
}
