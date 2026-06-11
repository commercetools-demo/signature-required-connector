package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;

/**
 * postDeploy lifecycle main. Idempotently provisions the custom Type and the cart API Extension.
 * Registration is also performed in-process at service startup (StartupRegistrar) so the connector
 * self-heals even if this lifecycle hook cannot run; both paths are idempotent.
 *
 * Exits non-zero on genuine failure so the deployment rolls back.
 */
public final class PostDeploy {

    public static void main(String[] args) {
        try {
            ProjectApiRoot apiRoot = ConnectorClient.build();
            String typeKey = ConnectorClient.env("CUSTOM_TYPE_KEY", "signature-required");
            String flagField = ConnectorClient.env("FLAG_FIELD_NAME", "signatureRequired");
            String extensionKey = ConnectorClient.env("EXTENSION_KEY", "signature-required-cart-extension");
            int timeoutMs = Integer.parseInt(ConnectorClient.env("EXTENSION_TIMEOUT_MS", "2000"));
            String secret = ConnectorClient.require("EXTENSION_AUTH_SECRET");
            String serviceUrl = ConnectorClient.require("CONNECT_SERVICE_URL");

            ConnectorRegistrar.ensureType(apiRoot, typeKey, flagField);
            ConnectorRegistrar.ensureExtension(apiRoot, extensionKey, serviceUrl, secret, timeoutMs);
            System.out.println("postDeploy completed successfully.");
        } catch (Exception e) {
            System.err.println("postDeploy failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private PostDeploy() {}
}
