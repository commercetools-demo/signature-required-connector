# Features

Code-derived inventory of what this repo implements. Bullets and key file paths —
the mechanism lives in `docs/how-it-works.md`, the walkthrough in `docs/demo-script.md`.

_Last generated: 2026-09-02 by feature-doc._

This repo is a commercetools **Connect** connector built from scratch (no starter base) — a
single Java 17 / Spring Boot 3 `service` application, registered as a cart **API Extension**,
that keeps a `signatureRequired` custom field on the cart (and, by extension, the order) in sync
with whether the cart contains a narcotic line item.

## Fail-closed signature-required gating (the distinctive capability)

- Scans every line item's product variant attributes for a configurable Boolean attribute
  (`NARCOTICS_ATTRIBUTE_NAME`, default `narcotics`) set to `true`, and derives whether the cart
  as a whole needs a signature on delivery (`service/src/main/java/com/commercetools/signature/service/SignatureFlagService.java`)
- Sets or clears a Boolean custom field (`FLAG_FIELD_NAME`, default `signatureRequired`) by
  returning the minimal `setCustomType`/`setCustomField` update action to commercetools — the
  connector never writes the cart itself and makes no commercetools call on the hot path beyond a
  cached Type-id lookup (`SignatureFlagService.evaluate`)
- **Fail-closed by design**: a malformed extension payload, an unresolved custom Type, a
  conflicting custom Type already on the cart, or any unexpected error all return **HTTP 400**,
  so commercetools rejects the cart/order operation rather than letting a narcotic order proceed
  unflagged (`SignatureFlagService.evaluate`, `web/GlobalExceptionHandler.java`)
- Detects when a cart already carries a *different* custom Type than the one configured and
  rejects with a clear `InvalidInput` message instead of silently overwriting the customer's own
  fields (`SignatureFlagService.evaluate`)
- Field never touches line items that lack the attribute, and clears the flag automatically once
  the last narcotic item is removed or the cart is emptied (`SignatureFlagService.evaluate`)

## commercetools data model

- Provisions a single custom Type scoped to commercetools' `order` resourceTypeId — which
  commercetools applies to both carts and orders — so the flag set on the cart at checkout time
  is carried onto the order automatically with no extra propagation logic
  (`service/src/main/java/com/commercetools/signature/connector/ConnectorRegistrar.ensureType`)
- Type/field provisioning is idempotent get-then-update: creates the Type only if absent, adds
  the field to an existing Type rather than recreating it, so a customer's pre-existing cart
  custom Type (pointed at via `CUSTOM_TYPE_KEY`) gains the field without disruption
  (`ConnectorRegistrar.ensureType`)
- Least-privilege API client scopes only — `manage_extensions` and `manage_types` — with no
  cart/order/product write scope, since the connector returns update actions rather than writing
  resources directly (`connect.yaml`)

## API Extension integration

- Registers a cart API Extension (`POST /service`) triggered on cart `Create`/`Update`, scoped
  with the condition `lineItems is not empty or custom is defined` so it isn't invoked on empty
  carts (`ConnectorRegistrar.ensureExtension`)
- Extension registration is update-in-place (change destination/triggers/timeout on the existing
  Extension) rather than delete-then-recreate, so there is never a window with no extension
  registered (`ConnectorRegistrar.ensureExtension`)
- Extension response timeout is configurable (`EXTENSION_TIMEOUT_MS`, default 2000ms, capped at
  10000ms) (`connect.yaml`, `ConnectorRegistrar.ensureExtension`)
- `GET /service/status` liveness endpoint is deliberately left unauthenticated for platform
  health checks, while `POST /service` requires auth (`web/ExtensionController.java`,
  `web/WebConfig.java`)
- Correlation-ID propagation: an inbound `X-Correlation-ID` header is placed into SLF4J MDC for
  request-scoped log tracing (`ExtensionController.handle`)

## Security

- Shared-secret bearer auth (`EXTENSION_AUTH_SECRET`) on the extension endpoint, checked with a
  constant-time byte comparison to avoid leaking the secret via timing
  (`web/AuthInterceptor.java`)
- No internal error detail, exception message, or stack trace is ever returned to the caller —
  every failure path returns a fixed, commercetools-shaped `errors` body
  (`web/GlobalExceptionHandler.java`)

## Connect deployment lifecycle

- `postDeploy` lifecycle main provisions the custom Type and registers the API Extension, run
  from the already-built Spring Boot fat jar via the `PropertiesLauncher` — no Maven, recompile,
  or dependency download at deploy time (`connect.yaml`,
  `connector/PostDeploy.java`)
- `preUndeploy` removes the API Extension on undeploy so a dangling fail-closed extension is
  never left pointing at a dead URL, while deliberately retaining the custom Type so existing
  orders keep their `signatureRequired` field (`connector/PreUndeploy.java`)
- In-process `StartupRegistrar` re-runs the same idempotent Type/Extension provisioning at
  service startup (after the web server is up), so the connector self-heals if the postDeploy
  hook didn't run; failures are logged (including the CTP HTTP status and granted scopes) but
  never crash the service (`connector/StartupRegistrar.java`)
- commercetools API credentials (`CTP_PROJECT_KEY`, `CTP_CLIENT_ID`, `CTP_CLIENT_SECRET`,
  `CTP_SCOPE`, `CTP_API_URL`, `CTP_AUTH_URL`) are auto-generated and injected by the platform via
  `inheritAs.apiClient.scopes` — the app only reads them, never declares or stores them
  (`connect.yaml`, `config/CtpConfig.java`)

## Local development & testing

- Unit tests cover the full business-logic decision matrix — narcotic vs. non-narcotic cart, no
  custom type attached, our type already attached, a conflicting foreign type — with no
  commercetools project required (`service/src/test/java/com/commercetools/signature/service/SignatureFlagServiceTest.java`)
- Router-level tests cover the auth rejection matrix (missing secret, wrong secret, open status
  route) and fail-closed HTTP behavior end-to-end via MockMvc, with the commercetools client and
  Type resolver mocked (`service/src/test/java/com/commercetools/signature/web/ExtensionControllerTest.java`)
- `local-test/call.sh` posts a sample API Extension payload to a locally running service to
  exercise the HTTP logic without a real commercetools project, using narcotic/normal cart JSON
  fixtures (`local-test/call.sh`, `local-test/narcotic-cart.json`, `local-test/normal-cart.json`)

## Operations / runbook

- README documents symptom-based recovery: extension erroring or unreachable (check deployment
  logs and `/service/status`), narcotic carts rejected for a conflicting custom type (point
  `CUSTOM_TYPE_KEY` at the customer's existing Type), no replay mechanism needed since the next
  cart update re-converges the flag from scratch, and an emergency-disable path (delete the
  deployment, which runs `preUndeploy` and removes the extension while retaining the Type)
  (`README.md`)

## Caveats (stubbed / gated, not full capabilities)

- Java connector support on commercetools Connect is currently gated and requires contacting
  commercetools support to enable for an organization; local Maven-based testing works regardless
  (`README.md`)
