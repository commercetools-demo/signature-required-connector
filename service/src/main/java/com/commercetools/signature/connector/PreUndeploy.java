package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.extension.Extension;
import java.util.List;

/**
 * preUndeploy: remove the cart API Extension so undeploy never leaves a dangling extension pointing
 * at a dead URL (which, being fail-closed, would block every cart operation).
 *
 * <p>The custom Type is intentionally <strong>retained</strong>: existing orders carry the
 * {@code signatureRequired} field, and deleting the Type while resources reference it would fail
 * anyway. Remove it manually if you are sure no cart/order uses it.
 */
public final class PreUndeploy {

    public static void main(String[] args) {
        try {
            run();
            System.out.println("preUndeploy completed successfully.");
        } catch (Exception e) {
            System.err.println("preUndeploy failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() {
        ProjectApiRoot apiRoot = ConnectorClient.build();
        String extensionKey = ConnectorClient.env("EXTENSION_KEY", "signature-required-cart-extension");
        String typeKey = ConnectorClient.env("CUSTOM_TYPE_KEY", "signature-required");

        List<Extension> existing = apiRoot.extensions().get()
                .withWhere("key = :key").withPredicateVar("key", extensionKey)
                .executeBlocking().getBody().getResults();

        if (existing.isEmpty()) {
            System.out.println("No API Extension '" + extensionKey + "' to remove.");
        } else {
            Extension extension = existing.get(0);
            apiRoot.extensions().withKey(extensionKey)
                    .delete().withVersion(extension.getVersion())
                    .executeBlocking();
            System.out.println("Deleted API Extension '" + extensionKey + "'.");
        }

        System.out.println("Custom Type '" + typeKey + "' retained (orders may still reference it).");
    }

    private PreUndeploy() {}
}
