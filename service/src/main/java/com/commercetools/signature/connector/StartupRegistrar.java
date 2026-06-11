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
        // Diagnostics: which platform-injected variables are present at runtime (names only).
        log.info("StartupRegistrar: CONNECT_SERVICE_URL set={}, CTP_API_URL set={}, CTP_AUTH_URL set={}, "
                        + "CTP_SCOPE set={}, CTP_PROJECT_KEY set={}",
                isSet("CONNECT_SERVICE_URL"), isSet("CTP_API_URL"), isSet("CTP_AUTH_URL"),
                isSet("CTP_SCOPE"), isSet("CTP_PROJECT_KEY"));
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
            // Don't crash the service; surface the error so it's visible in the deployment logs.
            log.error("StartupRegistrar failed to register Type/Extension: {}", e.getMessage(), e);
        }
    }

    private static boolean isSet(String name) {
        return StringUtils.hasText(System.getenv(name));
    }
}
