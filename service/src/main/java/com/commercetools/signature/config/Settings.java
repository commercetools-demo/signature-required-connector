package com.commercetools.signature.config;

/**
 * Validated, immutable runtime configuration for the signature-required logic.
 *
 * <p>Built once at startup from environment variables (see {@link AppConfig}) and injected
 * wherever needed. Keeping it a plain record makes the business logic trivially unit-testable
 * without a Spring context.
 */
public record Settings(
        String narcoticsAttribute,
        String customTypeKey,
        String flagFieldName,
        String extensionSecret) {

    public Settings {
        if (isBlank(narcoticsAttribute)) {
            throw new IllegalStateException("NARCOTICS_ATTRIBUTE_NAME must not be blank");
        }
        if (isBlank(customTypeKey)) {
            throw new IllegalStateException("CUSTOM_TYPE_KEY must not be blank");
        }
        if (isBlank(flagFieldName)) {
            throw new IllegalStateException("FLAG_FIELD_NAME must not be blank");
        }
        if (isBlank(extensionSecret)) {
            throw new IllegalStateException(
                    "EXTENSION_AUTH_SECRET is required: the extension endpoint refuses to start without "
                            + "a shared secret to authenticate commercetools calls.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
