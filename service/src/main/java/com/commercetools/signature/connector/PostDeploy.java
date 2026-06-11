package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.common.LocalizedString;
import com.commercetools.api.models.extension.ExtensionAction;
import com.commercetools.api.models.extension.ExtensionAuthorizationHeaderAuthenticationBuilder;
import com.commercetools.api.models.extension.ExtensionChangeDestinationActionBuilder;
import com.commercetools.api.models.extension.ExtensionChangeTriggersActionBuilder;
import com.commercetools.api.models.extension.ExtensionDestination;
import com.commercetools.api.models.extension.ExtensionDraft;
import com.commercetools.api.models.extension.ExtensionDraftBuilder;
import com.commercetools.api.models.extension.ExtensionHttpDestinationBuilder;
import com.commercetools.api.models.extension.ExtensionResourceTypeId;
import com.commercetools.api.models.extension.ExtensionSetTimeoutInMsActionBuilder;
import com.commercetools.api.models.extension.ExtensionTrigger;
import com.commercetools.api.models.extension.ExtensionTriggerBuilder;
import com.commercetools.api.models.extension.ExtensionUpdateBuilder;
import com.commercetools.api.models.type.CustomFieldBooleanTypeBuilder;
import com.commercetools.api.models.type.FieldDefinition;
import com.commercetools.api.models.type.FieldDefinitionBuilder;
import com.commercetools.api.models.type.ResourceTypeId;
import com.commercetools.api.models.type.Type;
import com.commercetools.api.models.type.TypeAddFieldDefinitionActionBuilder;
import com.commercetools.api.models.type.TypeDraft;
import com.commercetools.api.models.type.TypeDraftBuilder;
import com.commercetools.api.models.type.TypeUpdateBuilder;
import java.util.List;

/**
 * postDeploy: idempotently provision everything the connector needs (lifecycle-scripts.md).
 *
 * <ol>
 *   <li>Create the {@code cart}+{@code order} custom Type with the Boolean flag field, or add the
 *       field to an existing Type (never delete-then-recreate).</li>
 *   <li>Register the cart API Extension with destination auth and a trigger condition, or update it
 *       in place if it already exists (no gap window).</li>
 * </ol>
 *
 * Exits non-zero on genuine failure so the deployment rolls back.
 */
public final class PostDeploy {

    public static void main(String[] args) {
        try {
            run();
            System.out.println("postDeploy completed successfully.");
        } catch (Exception e) {
            System.err.println("postDeploy failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() {
        ProjectApiRoot apiRoot = ConnectorClient.build();

        String typeKey = ConnectorClient.env("CUSTOM_TYPE_KEY", "signature-required");
        String flagField = ConnectorClient.env("FLAG_FIELD_NAME", "signatureRequired");
        String extensionKey = ConnectorClient.env("EXTENSION_KEY", "signature-required-cart-extension");
        int timeoutMs = Integer.parseInt(ConnectorClient.env("EXTENSION_TIMEOUT_MS", "2000"));
        String secret = ConnectorClient.require("EXTENSION_AUTH_SECRET");
        String serviceUrl = extensionUrl(ConnectorClient.require("CONNECT_SERVICE_URL"));

        ensureType(apiRoot, typeKey, flagField);
        ensureExtension(apiRoot, extensionKey, serviceUrl, secret, timeoutMs);
    }

    /** Append the application endpoint to the platform-provided base URL if not already present. */
    private static String extensionUrl(String base) {
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed.endsWith("/service") ? trimmed : trimmed + "/service";
    }

    private static void ensureType(ProjectApiRoot apiRoot, String typeKey, String flagField) {
        List<Type> existing = apiRoot.types().get()
                .withWhere("key = :key").withPredicateVar("key", typeKey)
                .executeBlocking().getBody().getResults();

        FieldDefinition flag = FieldDefinitionBuilder.of()
                .name(flagField)
                .label(LocalizedString.ofEnglish("Signature Required"))
                .required(false)
                .type(CustomFieldBooleanTypeBuilder.of().build())
                .build();

        if (existing.isEmpty()) {
            // In commercetools, the "order" custom-type resource id covers BOTH carts and orders,
            // so a single resource id is enough for the flag to live on the cart and propagate to
            // the order. There is no separate "cart" ResourceTypeId.
            TypeDraft draft = TypeDraftBuilder.of()
                    .key(typeKey)
                    .name(LocalizedString.ofEnglish("Signature Required"))
                    .resourceTypeIds(ResourceTypeId.ORDER)
                    .fieldDefinitions(flag)
                    .build();
            apiRoot.types().post(draft).executeBlocking();
            System.out.println("Created custom Type '" + typeKey + "'.");
            return;
        }

        Type type = existing.get(0);
        boolean hasField = type.getFieldDefinitions().stream()
                .anyMatch(f -> f.getName().equals(flagField));
        if (hasField) {
            System.out.println("Custom Type '" + typeKey + "' already has field '" + flagField + "'.");
            return;
        }
        apiRoot.types().withKey(typeKey).post(TypeUpdateBuilder.of()
                .version(type.getVersion())
                .actions(TypeAddFieldDefinitionActionBuilder.of().fieldDefinition(flag).build())
                .build()).executeBlocking();
        System.out.println("Added field '" + flagField + "' to existing Type '" + typeKey + "'.");
    }

    private static void ensureExtension(ProjectApiRoot apiRoot, String extensionKey, String serviceUrl,
                                        String secret, int timeoutMs) {
        ExtensionDestination destination = ExtensionHttpDestinationBuilder.of()
                .url(serviceUrl)
                .authentication(ExtensionAuthorizationHeaderAuthenticationBuilder.of()
                        .headerValue("Bearer " + secret)
                        .build())
                .build();

        ExtensionTrigger trigger = ExtensionTriggerBuilder.of()
                .resourceTypeId(ExtensionResourceTypeId.CART)
                .actions(ExtensionAction.CREATE, ExtensionAction.UPDATE)
                // Only fire when there is something to evaluate: items present, or a custom type
                // already attached (so emptying a flagged cart still clears the flag).
                .condition("lineItems is not empty or custom is defined")
                .build();

        var existing = apiRoot.extensions().get()
                .withWhere("key = :key").withPredicateVar("key", extensionKey)
                .executeBlocking().getBody().getResults();

        if (existing.isEmpty()) {
            ExtensionDraft draft = ExtensionDraftBuilder.of()
                    .key(extensionKey)
                    .destination(destination)
                    .triggers(trigger)
                    .timeoutInMs(timeoutMs)
                    .build();
            apiRoot.extensions().post(draft).executeBlocking();
            System.out.println("Registered API Extension '" + extensionKey + "' -> " + serviceUrl);
            return;
        }

        var current = existing.get(0);
        apiRoot.extensions().withKey(extensionKey).post(ExtensionUpdateBuilder.of()
                .version(current.getVersion())
                .actions(
                        ExtensionChangeDestinationActionBuilder.of().destination(destination).build(),
                        ExtensionChangeTriggersActionBuilder.of().triggers(trigger).build(),
                        ExtensionSetTimeoutInMsActionBuilder.of().timeoutInMs(timeoutMs).build())
                .build()).executeBlocking();
        System.out.println("Updated API Extension '" + extensionKey + "' in place -> " + serviceUrl);
    }

    private PostDeploy() {}
}
