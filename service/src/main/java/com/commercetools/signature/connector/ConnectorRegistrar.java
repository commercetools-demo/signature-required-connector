package com.commercetools.signature.connector;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.common.LocalizedString;
import com.commercetools.api.models.extension.Extension;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotent provisioning of the custom Type and the cart API Extension, shared by the
 * {@link PostDeploy} lifecycle main and the in-process {@code StartupRegistrar}. All registration
 * is get-then-update (create only if absent) so re-running never opens a gap.
 */
public final class ConnectorRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistrar.class);

    private ConnectorRegistrar() {}

    /** Create the cart+order custom Type, or add the flag field to an existing Type. */
    public static void ensureType(ProjectApiRoot apiRoot, String typeKey, String flagField) {
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
            // The "order" custom-type resourceTypeId covers BOTH carts and orders.
            TypeDraft draft = TypeDraftBuilder.of()
                    .key(typeKey)
                    .name(LocalizedString.ofEnglish("Signature Required"))
                    .resourceTypeIds(ResourceTypeId.ORDER)
                    .fieldDefinitions(flag)
                    .build();
            apiRoot.types().post(draft).executeBlocking();
            log.info("Created custom Type '{}'.", typeKey);
            return;
        }

        Type type = existing.get(0);
        boolean hasField = type.getFieldDefinitions().stream().anyMatch(f -> f.getName().equals(flagField));
        if (hasField) {
            log.info("Custom Type '{}' already has field '{}'.", typeKey, flagField);
            return;
        }
        apiRoot.types().withKey(typeKey).post(TypeUpdateBuilder.of()
                .version(type.getVersion())
                .actions(TypeAddFieldDefinitionActionBuilder.of().fieldDefinition(flag).build())
                .build()).executeBlocking();
        log.info("Added field '{}' to existing Type '{}'.", flagField, typeKey);
    }

    /** Register the cart API Extension, or update it in place (no delete-then-recreate gap). */
    public static void ensureExtension(ProjectApiRoot apiRoot, String extensionKey, String connectServiceUrl,
                                       String secret, int timeoutMs) {
        String serviceUrl = extensionUrl(connectServiceUrl);
        ExtensionDestination destination = ExtensionHttpDestinationBuilder.of()
                .url(serviceUrl)
                .authentication(ExtensionAuthorizationHeaderAuthenticationBuilder.of()
                        .headerValue("Bearer " + secret).build())
                .build();

        ExtensionTrigger trigger = ExtensionTriggerBuilder.of()
                .resourceTypeId(ExtensionResourceTypeId.CART)
                .actions(ExtensionAction.CREATE, ExtensionAction.UPDATE)
                .condition("lineItems is not empty or custom is defined")
                .build();

        List<Extension> existing = apiRoot.extensions().get()
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
            log.info("Registered API Extension '{}' -> {}", extensionKey, serviceUrl);
            return;
        }

        Extension current = existing.get(0);
        apiRoot.extensions().withKey(extensionKey).post(ExtensionUpdateBuilder.of()
                .version(current.getVersion())
                .actions(
                        ExtensionChangeDestinationActionBuilder.of().destination(destination).build(),
                        ExtensionChangeTriggersActionBuilder.of().triggers(trigger).build(),
                        ExtensionSetTimeoutInMsActionBuilder.of().timeoutInMs(timeoutMs).build())
                .build()).executeBlocking();
        log.info("Updated API Extension '{}' in place -> {}", extensionKey, serviceUrl);
    }

    /** Delete the API Extension if present (used by preUndeploy). */
    public static void removeExtension(ProjectApiRoot apiRoot, String extensionKey) {
        List<Extension> existing = apiRoot.extensions().get()
                .withWhere("key = :key").withPredicateVar("key", extensionKey)
                .executeBlocking().getBody().getResults();
        if (existing.isEmpty()) {
            log.info("No API Extension '{}' to remove.", extensionKey);
            return;
        }
        Extension extension = existing.get(0);
        apiRoot.extensions().withKey(extensionKey).delete().withVersion(extension.getVersion()).executeBlocking();
        log.info("Deleted API Extension '{}'.", extensionKey);
    }

    /** Append the application endpoint to the platform-provided base URL if not already present. */
    static String extensionUrl(String base) {
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed.endsWith("/service") ? trimmed : trimmed + "/service";
    }
}
