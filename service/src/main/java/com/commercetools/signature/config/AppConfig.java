package com.commercetools.signature.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reads and validates non-secret + secret configuration from environment variables once at
 * startup. A missing required value fails the context creation loudly rather than producing a
 * cryptic 500 mid-request (see project-structure.md, Pattern 6).
 */
@Configuration
public class AppConfig {

    @Bean
    public Settings settings(
            @Value("${NARCOTICS_ATTRIBUTE_NAME:narcotics}") String narcoticsAttribute,
            @Value("${CUSTOM_TYPE_KEY:signature-required}") String customTypeKey,
            @Value("${FLAG_FIELD_NAME:signatureRequired}") String flagFieldName,
            @Value("${EXTENSION_AUTH_SECRET:}") String extensionSecret) {
        // The record's compact constructor performs the validation.
        return new Settings(narcoticsAttribute, customTypeKey, flagFieldName, extensionSecret);
    }
}
