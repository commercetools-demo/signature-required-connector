package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;

/**
 * preUndeploy lifecycle main. Removes the cart API Extension so undeploy never leaves a dangling
 * (fail-closed) extension pointing at a dead URL, which would block every cart operation.
 *
 * <p>The custom Type is intentionally retained: existing orders carry the {@code signatureRequired}
 * field, and deleting a Type that resources reference would fail anyway.
 */
public final class PreUndeploy {

    public static void main(String[] args) {
        try {
            ProjectApiRoot apiRoot = ConnectorClient.build();
            String extensionKey = ConnectorClient.env("EXTENSION_KEY", "signature-required-cart-extension");
            String typeKey = ConnectorClient.env("CUSTOM_TYPE_KEY", "signature-required");

            ConnectorRegistrar.removeExtension(apiRoot, extensionKey);
            System.out.println("Custom Type '" + typeKey + "' retained (orders may still reference it).");
            System.out.println("preUndeploy completed successfully.");
        } catch (Exception e) {
            System.err.println("preUndeploy failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private PreUndeploy() {}
}
