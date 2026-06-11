package com.commercetools.signature.service;

import static com.commercetools.signature.CartPayloads.NO_CUSTOM;
import static com.commercetools.signature.CartPayloads.envelope;
import static com.commercetools.signature.CartPayloads.foreignCustom;
import static com.commercetools.signature.CartPayloads.narcoticItem;
import static com.commercetools.signature.CartPayloads.normalItem;
import static com.commercetools.signature.CartPayloads.ourCustom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercetools.api.models.cart.CartSetCustomFieldAction;
import com.commercetools.api.models.cart.CartSetCustomTypeAction;
import com.commercetools.api.models.cart.CartUpdateAction;
import com.commercetools.signature.CartPayloads;
import com.commercetools.signature.config.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Business-logic matrix: the flag converges to the presence of any narcotic line item. */
class SignatureFlagServiceTest {

    private final ObjectMapper mapper = JsonUtils.createObjectMapper();
    private final Settings settings =
            new Settings("narcotics", "signature-required", "signatureRequired", "secret");

    /** Type is resolvable to our id (postDeploy already created it). */
    private final SignatureFlagService service =
            new SignatureFlagService(settings, () -> Optional.of(CartPayloads.OUR_TYPE_ID), mapper);

    private List<CartUpdateAction> actionsOf(String body) {
        ExtensionResult result = service.process(body);
        assertThat(result).isInstanceOf(ExtensionResult.Actions.class);
        return ((ExtensionResult.Actions) result).actions();
    }

    @Test
    void narcoticItem_noCustomYet_attachesTypeWithFlagTrue() {
        List<CartUpdateAction> actions = actionsOf(envelope(narcoticItem(), NO_CUSTOM));
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isInstanceOf(CartSetCustomTypeAction.class);
    }

    @Test
    void narcoticItem_flagAlreadyFalse_setsFlagTrue() {
        List<CartUpdateAction> actions = actionsOf(envelope(narcoticItem(), ourCustom(false)));
        assertThat(actions).hasSize(1);
        CartSetCustomFieldAction action = (CartSetCustomFieldAction) actions.get(0);
        assertThat(action.getName()).isEqualTo("signatureRequired");
        assertThat(action.getValue()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void narcoticItem_flagAlreadyTrue_noChange() {
        assertThat(actionsOf(envelope(narcoticItem(), ourCustom(true)))).isEmpty();
    }

    @Test
    void noNarcotic_flagWasTrue_clearsFlag() {
        List<CartUpdateAction> actions = actionsOf(envelope(normalItem(), ourCustom(true)));
        assertThat(actions).hasSize(1);
        CartSetCustomFieldAction action = (CartSetCustomFieldAction) actions.get(0);
        assertThat(action.getName()).isEqualTo("signatureRequired");
        assertThat(action.getValue()).isEqualTo(Boolean.FALSE);
    }

    @Test
    void noNarcotic_noCustom_noChange() {
        assertThat(actionsOf(envelope(normalItem(), NO_CUSTOM))).isEmpty();
    }

    @Test
    void mixedCart_oneNarcotic_attachesType() {
        List<CartUpdateAction> actions =
                actionsOf(envelope(normalItem() + "," + narcoticItem(), NO_CUSTOM));
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isInstanceOf(CartSetCustomTypeAction.class);
    }

    @Test
    void narcoticItem_conflictingForeignType_failsClosed() {
        ExtensionResult result = service.process(envelope(narcoticItem(), foreignCustom()));
        assertThat(result).isInstanceOf(ExtensionResult.Reject.class);
        assertThat(((ExtensionResult.Reject) result).code()).isEqualTo("InvalidInput");
    }

    @Test
    void noNarcotic_foreignType_leftUntouched() {
        assertThat(actionsOf(envelope(normalItem(), foreignCustom()))).isEmpty();
    }

    @Test
    void malformedEnvelope_throws() {
        assertThatThrownBy(() -> service.process("{\"action\":\"Update\",\"resource\":{\"typeId\":\"cart\"}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
