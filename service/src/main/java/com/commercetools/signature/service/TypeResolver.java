package com.commercetools.signature.service;

import java.util.Optional;

/**
 * Resolves the id of our configured custom Type (by key) so the handler can tell whether the
 * Type attached to a cart is ours. Implementations cache the result — this must never become a
 * per-request call on the cart hot path.
 */
public interface TypeResolver {
    Optional<String> resolveTypeId();
}
