package com.pgoogol.finance.currency.infrastructure.nbp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clients.nbp")
public record NbpProperties(String baseUrl, int requestsPerSecond, String table) {

}
