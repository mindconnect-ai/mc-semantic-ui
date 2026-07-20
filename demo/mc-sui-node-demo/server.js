// Pure Node.js / Express demo for semantic-ui.
//
// It serves a product list as a semantic-ui UiPage tree — plain JSON, the exact
// same shape a Spring Boot backend would return. There is NO semantic-ui code on
// the server: this file just builds the JSON. The browser-side SuiRenderer (in
// /public/sui, copied from the core module) paints it.
import express from "express";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json());

// In-memory "database" — no Postgres, no ORM.
let products = [
  {id: "p-1", sku: "CHAIR-01", name: "Office Chair", price: "299,00 €"},
  {id: "p-2", sku: "DESK-14", name: "Standing Desk", price: "649,00 €"},
  {id: "p-3", sku: "LAMP-07", name: "Desk Lamp", price: "" + "59,00 €"},
  {id: "p-4", sku: "MON-27", name: "27\" Monitor", price: "389,00 €"},
  {id: "p-5", sku: "KEY-MX", name: "Mechanical Keyboard", price: "129,00 €"},
];

// Build the product-list UiPage tree.
function productListPage(query) {
  const q = (query ?? "").toLowerCase();
  const rows = products
    .filter(p => !q || p.sku.toLowerCase().includes(q) || p.name.toLowerCase().includes(q))
    .map(p => ({type: "row", id: p.id, data: p}));

  const search = {
    type: "form", id: "product-search",
    fields: [{type: "field", id: "q", label: "Search", fieldType: "TEXT",
              value: query ?? "", editable: true}],
    actions: [{type: "action", id: "search", label: "Search", style: "PRIMARY",
               onClick: {url: "/products", method: "GET"}}],
  };

  const table = {
    type: "table", id: "products-table", title: "Products",
    columns: [
      {type: "column", id: "col-sku", label: "SKU", dataKey: "sku",
       cellTemplate: {type: "link", id: "sku-link",
                      href: "/products/{id}", label: "{sku}"}},
      {type: "column", id: "col-name", label: "Name", dataKey: "name"},
      {type: "column", id: "col-price", label: "Price", dataKey: "price"},
    ],
    rows,
    rowActions: [
      {type: "action", id: "delete", label: "Delete", style: "DANGER",
       confirm: "Delete this product?",
       onClick: {url: "/products/{id}", method: "DELETE"}},
    ],
  };

  return {
    type: "page",
    navigate: "/products",
    node: {type: "stack", id: "product-page", children: [search, table]},
  };
}

// Build a product-detail UiPage that renders as a modal dialog.
function productDetailPage(product) {
  return {
    type: "page",
    navigate: "/products",
    dialog: {
      title: product.name,
      closeHref: "/products",
      node: {
        type: "detail", id: "product-detail",
        fields: [
          {type: "field", id: "d-sku", label: "SKU", fieldType: "TEXT", value: product.sku},
          {type: "field", id: "d-name", label: "Name", fieldType: "TEXT", value: product.name},
          {type: "field", id: "d-price", label: "Price", fieldType: "TEXT", value: product.price},
        ],
      },
    },
  };
}

// GET /products — returns the UiPage as JSON for the SPA renderer.
app.get("/products", (req, res) => {
  res.json(productListPage(req.query.q));
});

// GET /products/:id — returns the product detail as a modal dialog.
app.get("/products/:id", (req, res) => {
  const product = products.find(p => p.id === req.params.id);
  if (!product) return res.status(404).json(productListPage());
  res.json(productDetailPage(product));
});

// DELETE /products/:id — removes a product, then returns the refreshed list.
app.delete("/products/:id", (req, res) => {
  products = products.filter(p => p.id !== req.params.id);
  res.json(productListPage());
});

// Serve the client assets (/sui/*) and the static shell (index.html).
app.use("/sui", express.static(resolve(here, "public/sui")));
app.use(express.static(resolve(here, "public")));

const port = process.env.PORT ?? 3000;
app.listen(port, () => {
  console.log(`mc-sui-node-demo running at http://localhost:${port}/`);
});
