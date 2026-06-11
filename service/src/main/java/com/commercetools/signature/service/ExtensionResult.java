package com.commercetools.signature.service;

import com.commercetools.api.models.cart.CartUpdateAction;
import java.util.List;

/**
 * Outcome of evaluating a cart. Either a (possibly empty) set of update actions to converge the
 * flag, or a fail-closed rejection that the controller turns into a 400 so the cart operation is
 * blocked.
 */
public sealed interface ExtensionResult {

    /** Success: return these update actions to commercetools (empty list = no change). */
    record Actions(List<CartUpdateAction> actions) implements ExtensionResult {}

    /** Fail-closed rejection: the cart operation must not proceed. */
    record Reject(String code, String message) implements ExtensionResult {}
}
