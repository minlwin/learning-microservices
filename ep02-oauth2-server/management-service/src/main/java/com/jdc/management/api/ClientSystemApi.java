package com.jdc.management.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jdc.management.props.ClientSystemProps;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/setting/systems")
public class ClientSystemApi {

	private final ClientSystemProps props;
	
	@GetMapping
	public List<String> getAvailableSystem() {
		return props.getSystems();
	}

}
