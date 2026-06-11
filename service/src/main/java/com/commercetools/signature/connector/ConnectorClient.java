package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import com.commercetools.api.defaultconfig.ServiceRegion;
import io.vrap.rmf.base.client.oauth2.ClientCredentials;

/**
 * Builds a {@link ProjectApiRoot} from environment variables for the one-shot lifecycle mains
 * ({@link PostDeploy} / {@link PreUndeploy}). These run via Maven outside Spring, so they read the
 * platform-injected {@code CTP_*} variables directly.
 */
final class ConnectorClient {

    private ConnectorClient() {}

    static ProjectApiRoot build() {
        String projectKey = require("CTP_PROJECT_KEY");
        String clientId = require("CTP_CLIENT_ID");
        String clientSecret = require("CTP_CLIENT_SECRET");
        String scope = env("CTP_SCOPE", "");
        String apiUrl = env("CTP_API_URL", "");
        String authUrl = env("CTP_AUTH_URL", "");
        String region = env("CTP_REGION", "");

        var credentials = ClientCredentials.of()
                .withClientId(clientId)
                .withClientSecret(clientSecret);
        if (!scope.isBlank()) {
            credentials = credentials.withScopes(scope);
        }

        ApiRootBuilder builder;
        if (!apiUrl.isBlank() && !authUrl.isBlank()) {
            builder = ApiRootBuilder.of().defaultClient(credentials.build(), authUrl, apiUrl);
        } else if (!region.isBlank()) {
            builder = ApiRootBuilder.of().defaultClient(credentials.build(), ServiceRegion.valueOf(region));
        } else {
            throw new IllegalStateException(
                    "Provide CTP_API_URL + CTP_AUTH_URL (injected by Connect) or CTP_REGION (local dev).");
        }
        return builder.build(projectKey);
    }

    static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    static String require(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable " + key + " is not set");
        }
        return value;
    }
}
