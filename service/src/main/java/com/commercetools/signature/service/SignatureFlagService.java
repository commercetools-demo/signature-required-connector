package com.commercetools.signature.service;

import com.commercetools.api.models.cart.Cart;
import com.commercetools.api.models.cart.CartSetCustomFieldActionBuilder;
import com.commercetools.api.models.cart.CartSetCustomTypeActionBuilder;
import com.commercetools.api.models.cart.CartUpdateAction;
import com.commercetools.api.models.cart.LineItem;
import com.commercetools.api.models.product.Attribute;
import com.commercetools.api.models.product.ProductVariant;
import com.commercetools.api.models.type.CustomFields;
import com.commercetools.api.models.type.FieldContainer;
import com.commercetools.api.models.type.TypeResourceIdentifierBuilder;
import com.commercetools.signature.config.Settings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core business logic: keep the {@code signatureRequired} flag on a cart in sync with whether any
 * line item's product variant carries the configured narcotics attribute set to {@code true}.
 *
 * <p>The flag is stored as a Boolean custom field on a Type scoped to both {@code cart} and
 * {@code order}, so it propagates automatically onto the order when the cart is ordered.
 *
 * <p>Pure, payload-only logic — no commercetools call on the hot path apart from the cached Type-id
 * resolution. The result is the minimal set of update actions (or none when already correct).
 */
@Service
public class SignatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(SignatureFlagService.class);

    private final Settings settings;
    private final TypeResolver typeResolver;
    private final ObjectMapper ctpMapper;

    public SignatureFlagService(Settings settings, TypeResolver typeResolver, ObjectMapper ctpMapper) {
        this.settings = settings;
        this.typeResolver = typeResolver;
        this.ctpMapper = ctpMapper;
    }

    /**
     * Parse an API Extension request body and evaluate it.
     *
     * @throws IllegalArgumentException if the envelope is malformed (turned into a fail-closed 400)
     */
    public ExtensionResult process(String requestBody) {
        Cart cart = parseCart(requestBody);
        return evaluate(cart);
    }

    private Cart parseCart(String requestBody) {
        try {
            JsonNode root = ctpMapper.readTree(requestBody);
            JsonNode objNode = root.path("resource").path("obj");
            if (objNode.isMissingNode() || objNode.isNull()) {
                throw new IllegalArgumentException("Extension payload is missing resource.obj");
            }
            String resourceType = root.path("resource").path("typeId").asText("");
            if (!"cart".equals(resourceType)) {
                throw new IllegalArgumentException("Unexpected resource type: '" + resourceType + "' (expected cart)");
            }
            return ctpMapper.treeToValue(objNode, Cart.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse cart from extension payload: " + e.getMessage(), e);
        }
    }

    /** Decide the update actions (or rejection) for a parsed cart. Visible for unit testing. */
    public ExtensionResult evaluate(Cart cart) {
        boolean needsFlag = cartContainsNarcotic(cart);

        CustomFields custom = cart.getCustom();
        String attachedTypeId = (custom != null && custom.getType() != null) ? custom.getType().getId() : null;
        Optional<String> ourTypeId = typeResolver.resolveTypeId();
        boolean isOurs = attachedTypeId != null && ourTypeId.isPresent() && attachedTypeId.equals(ourTypeId.get());
        Boolean currentFlag = isOurs ? readFlag(custom.getFields()) : null;

        if (needsFlag) {
            if (custom == null) {
                // No custom type attached yet → attach ours with the flag set.
                return actions(CartSetCustomTypeActionBuilder.of()
                        .type(TypeResourceIdentifierBuilder.of().key(settings.customTypeKey()).build())
                        .fields(FieldContainer.builder().addValue(settings.flagFieldName(), Boolean.TRUE).build())
                        .build());
            }
            if (isOurs) {
                if (!Boolean.TRUE.equals(currentFlag)) {
                    return actions(setFlag(Boolean.TRUE));
                }
                return noChange();
            }
            // A different custom Type is attached and we can't safely overwrite it. Fail closed:
            // a narcotic cart must not proceed unflagged.
            log.warn("Narcotic cart {} has a conflicting custom type (id={}); cannot set '{}'.",
                    cart.getId(), attachedTypeId, settings.flagFieldName());
            return new ExtensionResult.Reject("InvalidInput",
                    "Cannot set '" + settings.flagFieldName() + "': the cart already has a different custom type. "
                            + "Set CUSTOM_TYPE_KEY to your cart's custom Type so the field can live alongside your fields.");
        }

        // No narcotic item present → clear the flag only if WE set it to true.
        if (isOurs && Boolean.TRUE.equals(currentFlag)) {
            return actions(setFlag(Boolean.FALSE));
        }
        return noChange();
    }

    private boolean cartContainsNarcotic(Cart cart) {
        List<LineItem> lineItems = cart.getLineItems();
        if (lineItems == null) {
            return false;
        }
        return lineItems.stream().anyMatch(this::lineItemIsNarcotic);
    }

    private boolean lineItemIsNarcotic(LineItem lineItem) {
        ProductVariant variant = lineItem.getVariant();
        if (variant == null || variant.getAttributes() == null) {
            return false;
        }
        return variant.getAttributes().stream().anyMatch(this::isNarcoticAttribute);
    }

    private boolean isNarcoticAttribute(Attribute attribute) {
        return settings.narcoticsAttribute().equals(attribute.getName()) && isTrue(attribute.getValue());
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private Boolean readFlag(FieldContainer fields) {
        if (fields == null || fields.values() == null) {
            return null;
        }
        Object value = fields.values().get(settings.flagFieldName());
        return value instanceof Boolean b ? b : (value instanceof String s ? Boolean.valueOf(s) : null);
    }

    private CartUpdateAction setFlag(Boolean value) {
        return CartSetCustomFieldActionBuilder.of()
                .name(settings.flagFieldName())
                .value(value)
                .build();
    }

    private static ExtensionResult actions(CartUpdateAction... actions) {
        return new ExtensionResult.Actions(List.of(actions));
    }

    private static ExtensionResult noChange() {
        return new ExtensionResult.Actions(List.of());
    }
}
