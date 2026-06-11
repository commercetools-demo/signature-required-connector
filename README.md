# signature-required (commercetools Connect)

A production-ready commercetools **Connect** connector that keeps a `signatureRequired` flag in
sync with whether a cart contains any **narcotic** product — so the flag is present on the order at
fulfilment time and a signature can be required on delivery.

> One `service` application registered as a cart **API Extension**. Java 17 + Spring Boot 3, built
> with Maven.

## What it does

- On every cart `Create`/`Update`, commercetools calls this extension **before persisting**.
- The extension scans each line item's product **variant attributes** for a Boolean attribute named
  `narcotics` (configurable) set to `true`.
- If **any** line item is narcotic → it sets the Boolean custom field `signatureRequired = true` on
  the cart.
- When the last narcotic item is removed (or the cart is emptied) → it sets `signatureRequired = false`.
- Line items without the attribute are never affected.
- The flag lives on a custom Type scoped to **both `cart` and `order`**, so when the cart is ordered
  the flag is **automatically copied onto the order** — no extra work needed.

It does **no external calls on the hot path** (decision is made purely from the cart payload), apart
from a single cached lookup of the custom Type's id. That keeps it comfortably inside the extension
response budget.

## Fail-open vs fail-closed

**This connector is fail-closed.** If the extension cannot evaluate a cart — a malformed payload, an
unresolved custom Type, a conflicting custom Type already on the cart, or any unexpected error — it
returns **HTTP 400** and commercetools **rejects the cart/order operation**. This is deliberate for a
compliance feature: a narcotic order must never proceed unflagged.

**Operational consequence:** if this service is down or erroring, cart operations that trigger it
will fail. Detection and recovery are in [Runbook](#runbook).

## Configuration

All keys are declared in [`connect.yaml`](./connect.yaml).

| Key | Secured | Required | Default | Meaning |
|---|---|---|---|---|
| `EXTENSION_AUTH_SECRET` | ✅ | yes | — | Shared secret commercetools sends as `Authorization: Bearer …`. The endpoint rejects calls without it. |
| `NARCOTICS_ATTRIBUTE_NAME` | | no | `narcotics` | Boolean variant attribute that marks a product as narcotic. |
| `CUSTOM_TYPE_KEY` | | no | `signature-required` | Key of the cart+order custom Type holding the flag. **Point this at your existing cart custom Type** if you already use one (the field is added to it). |
| `FLAG_FIELD_NAME` | | no | `signatureRequired` | Name of the Boolean custom field. |
| `EXTENSION_KEY` | | no | `signature-required-cart-extension` | Key for the registered API Extension. |
| `EXTENSION_TIMEOUT_MS` | | no | `2000` | Extension response timeout (max 10000). |

commercetools credentials (`CTP_PROJECT_KEY`, `CTP_CLIENT_ID`, `CTP_CLIENT_SECRET`, `CTP_SCOPE`,
`CTP_API_URL`, `CTP_AUTH_URL`) are **auto-generated and injected** by the platform from
`inheritAs.apiClient.scopes` — you do not supply them.

## Required scopes

Least-privilege, via `inheritAs.apiClient.scopes`:

- `manage_extensions` — register/update the cart API Extension (postDeploy).
- `manage_types` — create the custom Type and read it at runtime.

No order/cart/product write scope is needed: the connector never writes carts directly — it returns
update actions that commercetools applies.

## Data model

`postDeploy` creates (idempotently) a custom Type:

- key: `CUSTOM_TYPE_KEY`, resourceTypeId: `order`
- field: `FLAG_FIELD_NAME` — Boolean, optional

> commercetools nuance: the custom-type `order` resourceTypeId applies to **both carts and orders**
> (there is no separate `cart` resourceTypeId). So one Type covers the cart and the field is carried
> onto the order automatically when the cart is ordered.

If a Type with that key already exists, the field is **added** to it (the Type is never recreated).
If your carts already use a different custom Type, set `CUSTOM_TYPE_KEY` to **that** Type's key so
the flag coexists with your own fields. (A narcotic cart bearing a *different* custom Type than the
configured one is rejected — fail-closed — with a clear message.)

## Local development & testing

> ⚠️ **Java on Connect is gated.** Connect supports Java (17 / Spring Boot 3, built with Maven), but
> enabling Java connectors for your organization currently requires contacting
> [commercetools support](https://support.commercetools.com/). The exact build/run wiring for Java
> apps should be confirmed with them. Everything below runs locally with plain Maven regardless.

### Unit + router tests (no commercetools project needed)

```bash
cd service
mvn test
```

Covers the business-logic matrix and the router-level auth rejection matrix + fail-closed behavior.

### Run the service locally and simulate commercetools

1. Start it with a local secret (no real CTP project required just to exercise the HTTP logic —
   the Type lookup will simply log a warning and, for narcotic carts, fail closed):

   ```bash
   cd service
   EXTENSION_AUTH_SECRET=local-secret \
   CTP_PROJECT_KEY=your-project CTP_CLIENT_ID=… CTP_CLIENT_SECRET=… \
   CTP_REGION=GCP_EUROPE_WEST1 \
   mvn spring-boot:run
   ```

2. POST a sample extension payload (see [`local-test/`](./local-test)):

   ```bash
   ./local-test/call.sh local-test/narcotic-cart.json local-secret
   ```

   A narcotic cart returns `{"actions":[{"action":"setCustomType",…}]}`; a normal cart returns
   `{"actions":[]}`.

See the official guide for the two ways to test a service app locally:
<https://docs.commercetools.com/connect/steps-locally-test-service>.

## Deploy

```bash
npm install -g @commercetools/cli
commercetools auth login --client-credentials --client-id <id> --client-secret <secret> \
  --region <region> --project-key <key>

commercetools connect validate                       # validate connect.yaml + the app
commercetools connect connectorstaged create \
  --repository-url <git-url> --repository-tag <tag> --creator-email <you> --name signature-required
commercetools connect connectorstaged preview --key <connector-key> --deployment-key <dep-key> --region <region>
# once verified:
commercetools connect connectorstaged publish --key <connector-key>
commercetools connect deployment create --connector-key <connector-key> --region <region> --type sandbox
```

Deploy in the **same region** as your project. After deploy, `postDeploy` registers the Type and the
extension. Config changes → `commercetools connect deployment redeploy …` (postDeploy is idempotent).

## Runbook

This is a synchronous extension, not a queue consumer — there is no DLQ. Failure handling:

- **Symptom: carts/checkout suddenly failing after deploy.** The fail-closed extension is erroring or
  unreachable. Check `commercetools connect deployment logs --application service`, and the extension
  destination (`/service/status` should return 200). Fix and `redeploy`.
- **Symptom: narcotic carts rejected with "conflicting custom type".** Your carts use a different
  custom Type. Set `CUSTOM_TYPE_KEY` to that Type's key and `redeploy`.
- **Replay:** no replay needed — the next cart update re-evaluates from scratch and converges the
  flag. There is no accumulated state to repair.
- **Emergency disable:** delete the deployment (runs `preUndeploy`, which removes the extension so
  carts stop being blocked). The custom Type is retained so existing order flags survive.

## Caveats

- Java connector support is gated (see above).
- commercetools Java SDK version is pinned in [`service/pom.xml`](./service/pom.xml); bump if needed.
