package com.commercetools.signature.config;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import com.commercetools.api.defaultconfig.ServiceRegion;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.oauth2.ClientCredentials;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * commercetools client wiring.
 *
 * <p>At install time the platform injects {@code CTP_PROJECT_KEY}, {@code CTP_CLIENT_ID},
 * {@code CTP_CLIENT_SECRET}, {@code CTP_SCOPE}, {@code CTP_API_URL} and {@code CTP_AUTH_URL}
 * (from {@code inheritAs.apiClient.scopes}). For local development you can instead set
 * {@code CTP_REGION} to a {@link ServiceRegion} enum name (e.g. {@code GCP_EUROPE_WEST1}).
 *
 * <p>The runtime only uses this client for a single, cached "resolve our custom Type id"
 * lookup — never on the cart hot path.
 */
@Configuration
public class CtpConfig {

    /** The SDK's ObjectMapper knows how to (de)serialize commercetools models incl. polymorphic update actions. */
    @Bean
    public ObjectMapper ctpObjectMapper() {
        return JsonUtils.createObjectMapper();
    }

    @Bean
    public ProjectApiRoot projectApiRoot(
            @Value("${CTP_PROJECT_KEY:}") String projectKey,
            @Value("${CTP_CLIENT_ID:}") String clientId,
            @Value("${CTP_CLIENT_SECRET:}") String clientSecret,
            @Value("${CTP_SCOPE:}") String scope,
            @Value("${CTP_API_URL:}") String apiUrl,
            @Value("${CTP_AUTH_URL:}") String authUrl,
            @Value("${CTP_REGION:}") String region) {

        require(projectKey, "CTP_PROJECT_KEY");
        require(clientId, "CTP_CLIENT_ID");
        require(clientSecret, "CTP_CLIENT_SECRET");

        var credentials = ClientCredentials.of()
                .withClientId(clientId)
                .withClientSecret(clientSecret);
        if (StringUtils.hasText(scope)) {
            credentials = credentials.withScopes(scope);
        }

        ApiRootBuilder builder;
        if (StringUtils.hasText(apiUrl) && StringUtils.hasText(authUrl)) {
            builder = ApiRootBuilder.of().defaultClient(credentials.build(), authUrl, apiUrl);
        } else if (StringUtils.hasText(region)) {
            builder = ApiRootBuilder.of().defaultClient(credentials.build(), ServiceRegion.valueOf(region));
        } else {
            throw new IllegalStateException(
                    "Provide CTP_API_URL + CTP_AUTH_URL (injected by Connect) or CTP_REGION (local dev).");
        }
        return builder.build(projectKey);
    }

    private static void require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required to build the commercetools client");
        }
    }
}
