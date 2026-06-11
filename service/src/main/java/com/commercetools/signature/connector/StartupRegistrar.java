package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.signature.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Performs the idempotent Type + API Extension registration in-process at service startup, after
 * the web server is up. This is the observable counterpart to {@link PostDeploy}: its logs appear
 * in the normal deployment log stream, and it makes the connector self-heal even when the
 * lifecycle hook cannot run. Failures are logged but do not crash the service.
 */
@Component
public class StartupRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRegistrar.class);

    private final ProjectApiRoot apiRoot;
    private final Settings settings;
    private final String connectServiceUrl;
    private final String extensionKey;
    private final int extensionTimeoutMs;

    public StartupRegistrar(
            ProjectApiRoot apiRoot,
            Settings settings,
            @Value("${CONNECT_SERVICE_URL:}") String connectServiceUrl,
            @Value("${EXTENSION_KEY:signature-required-cart-extension}") String extensionKey,
            @Value("${EXTENSION_TIMEOUT_MS:2000}") int extensionTimeoutMs) {
        this.apiRoot = apiRoot;
        this.settings = settings;
        this.connectServiceUrl = connectServiceUrl;
        this.extensionKey = extensionKey;
        this.extensionTimeoutMs = extensionTimeoutMs;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Diagnostics: which platform-injected variables are present at runtime, and the granted
        // scopes (scopes are not secret) so we can confirm manage_types/manage_extensions applied.
        log.info("StartupRegistrar: CONNECT_SERVICE_URL set={}, CTP_API_URL={}, CTP_AUTH_URL={}, "
                        + "CTP_PROJECT_KEY={}, CTP_SCOPE='{}'",
                isSet("CONNECT_SERVICE_URL"), System.getenv("CTP_API_URL"), System.getenv("CTP_AUTH_URL"),
                System.getenv("CTP_PROJECT_KEY"), System.getenv("CTP_SCOPE"));
        try {
            ConnectorRegistrar.ensureType(apiRoot, settings.customTypeKey(), settings.flagFieldName());

            if (StringUtils.hasText(connectServiceUrl)) {
                ConnectorRegistrar.ensureExtension(
                        apiRoot, extensionKey, connectServiceUrl, settings.extensionSecret(), extensionTimeoutMs);
            } else {
                log.warn("CONNECT_SERVICE_URL is not set at runtime — skipping API Extension registration. "
                        + "The extension must then be registered by the postDeploy lifecycle script.");
            }
            log.info("StartupRegistrar completed.");
        } catch (Exception e) {
            // Don't crash the service; surface a concise, complete error in the deployment logs.
            Throwable cause = e;
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof io.vrap.rmf.base.client.ApiHttpException api) {
                String body = api.getMessage() == null ? "" : api.getMessage();
                int max = Math.min(body.length(), 600);
                log.error("StartupRegistrar failed: commercetools returned HTTP {} for the registration call. "
                        + "Detail (first {} chars): {}", api.getStatusCode(), max, body.substring(0, max));
            } else {
                log.error("StartupRegistrar failed ({}): {}", cause.getClass().getName(), cause.getMessage());
            }
        }
    }

    private static boolean isSet(String name) {
        return StringUtils.hasText(System.getenv(name));
    }
}
