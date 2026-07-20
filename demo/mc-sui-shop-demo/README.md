# SUI Shop Demo

Tiny Spring Boot app that demonstrates the **server-side Handlebars renderer**
of `mc-semantic-ui-core` end-to-end. Product CRUD (list with search +
pagination, create, view, edit, delete) backed by Postgres / JPA. **No
JavaScript at all** — every interaction is a plain HTML link or form post.

## What it shows

- `UiPage` / `UiList` / `UiTable` / `UiForm` / `UiDetail` / `UiSection` /
  `UiAction` all rendered via the bundled `.hbs` templates in
  `core/mc-semantic-ui-core/src/main/resources/templates/sui/`.
- Content negotiation: the same `UiPage`-returning controller method serves
  HTML for `Accept: text/html` (browser) and would serve JSON for a SPA client
  (`Accept: application/json`) — without controller changes. The HTML
  converter is wired automatically by `SuiSsrAutoConfiguration` when
  `mindconnect.sui.ssr.enabled=true`.
- `_method` hidden field for `DELETE` and `PUT` via Spring's
  `HiddenHttpMethodFilter` (also auto-registered).
- Post/Redirect/Get on every mutation so URLs stay accurate and reloads don't
  resubmit forms.

## Run it

```bash
# 1. Postgres (podman, defensive down + up — mirrors admin-ui-app's start-keycloak.sh)
cd semantic-ui/demo/mc-sui-shop-demo
cp .env.docker.example .env.docker   # if not already there
./start-postgres.sh

# 2. App
mvn -f pom.xml spring-boot:run

# 3. Browser
open http://localhost:8080/   # redirects to /admin/products
```

The first boot seeds ~8 sample products. Restart-safe (skipped when the
table already has rows).

To stop Postgres later: `./stop-postgres.sh` (keeps the data volume).
Pass `--clean` to drop the volume too (fresh DB + re-seed on next start).

## Known SSR limitations (Iteration 1)

These are documented in the templates with `SSR limitation:` comments — they
exist because the templates are intentionally logic-less:

- **Pagination buttons** carry the raw `{page}` placeholder server-side; the
  per-button substitution would need template-side arithmetic. Next/Prev
  still navigate (they share the same trigger), but they don't carry the
  rewritten page number until a SPA client takes over.
- **Row actions in tables** carry the raw `{id}` placeholder for the same
  reason — `view`/`edit`/`delete` links in the product table won't resolve
  to the row id under pure SSR.

Both fall away once the planned view-model preparator step lands (small
Java helper that expands `{page}` / `{id}` per row before rendering). The
view-model step is intentionally *not* part of Iteration 1 because pure SSR
without it is enough to prove the renderer wiring.

## Layout

```
src/main/java/ai/mindconnect/sui/demo/shop/
  ShopDemoApplication.java   ← @SpringBootApplication
  IndexController.java       ← /  →  /admin/products
  ProductController.java     ← /admin/products/** CRUD
  ProductService.java        ← @Transactional CRUD ops
  ProductRepository.java     ← Spring Data JPA + search query
  Product.java               ← @Entity
  SampleDataLoader.java      ← initial seed
  ProductListPage.java       ← UiPage builder for the list view
  ProductFormPage.java       ←  …             new/edit form
  ProductDetailPage.java     ←  …             read-only detail
src/main/resources/
  application.yaml           ← DSN + mindconnect.sui.ssr.enabled=true
docker-compose.yml           ← Postgres on port 5433
.env.docker.example          ← copy to .env.docker
start-postgres.sh            ← podman compose up, idempotent
stop-postgres.sh             ← podman compose down [+ --clean to drop volume]
```

## Stopping / cleaning up

```bash
./stop-postgres.sh             # stop Postgres, keep data volume
./stop-postgres.sh --clean     # drop postgres_data too (next start re-seeds)
```
