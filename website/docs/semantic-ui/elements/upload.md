---
title: Upload
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# `upload` — a drag-and-drop file zone

**`UiUpload`** renders a drop area with a prompt, a browse button and a hidden
`<input type="file">`. The event bus wires both paths — dropping files onto the
zone and picking them through the button — and fires `onUpload` as soon as files
arrive. There is no separate "start upload" step.

Reach for it when file intake is the point of the screen. For a single inline
picker inside an ordinary form, a [`field`](./field.md) of type `FILE` is the
smaller node.

<iframe
  src="/mc-semantic-ui/embed/node.html?spec=upload"
  title="Live: UiUpload"
  loading="lazy"
  style={{width: '100%', height: '280px', border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: '8px'}}
></iframe>

*Live — drop image files on the zone, or use "Choose files…". Nothing is sent
anywhere: `onUpload` is an `INVOKE` trigger whose handler reads the `File`
objects in the browser and patches their names into the line below.*

## Fields

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Node id and the zone's DOM id. Also the default multipart part name. |
| `label` | `String` | Caption above the drop zone. Omitted when absent. |
| `hint` | `String` | Small helper text under the zone, e.g. "PNG or JPG, max 5 MB". |
| `name` | `String` | Multipart field name the files are sent under. Defaults to `id`. |
| `accept` | `String` | HTML `accept` filter (`"image/*"`, `".pdf,.docx"`). Null = any file. |
| `multiple` | `boolean` | Allow more than one file per drop/pick. Defaults to `false`. |
| `buttonLabel` | `String` | Browse-button text. Renderer default: `"Browse…"`. |
| `dropText` | `String` | Prompt inside the zone. Renderer default: `"Drag files here or"`. |
| `onUpload` | `UiTrigger` | Fired when files are dropped or picked. Usually `UPLOAD`; `INVOKE` for client-only handling. |
| `cssClass` | `String` | Extra CSS class on the zone wrapper. |

## Building one

<Tabs groupId="ui-lang">
<TabItem value="java" label="Java">

```java
// The common case: multipart POST the files, apply the returned page/patch.
UiUpload.of("images", "Product images")
        .hint("PNG or JPG, max 5 MB each.")
        .accept("image/*")
        .multiple()
        .dropText("Drag images here or")
        .buttonLabel("Choose files…")
        .uploadTo("/api/products/42/images");

// Same thing spelled out, with a custom part name and method:
UiUpload.of("contract", "Signed contract")
        .name("file")
        .accept(".pdf")
        .onUpload(UiTrigger.upload("PUT", "/api/contracts/7"));

// Client-only: the handler receives the File objects, no backend involved.
UiUpload.of("preview", "Preview an image")
        .accept("image/*")
        .onUpload(UiTrigger.invoke("previewImage"));
```

</TabItem>
<TabItem value="json" label="JSON">

```json
{ "type": "upload", "id": "images", "label": "Product images",
  "hint": "PNG or JPG, max 5 MB each.",
  "name": "files", "accept": "image/*", "multiple": true,
  "dropText": "Drag images here or", "buttonLabel": "Choose files…",
  "onUpload": { "behavior": "UPLOAD", "method": "POST",
                "url": "/api/products/42/images" } }

{ "type": "upload", "id": "preview", "label": "Preview an image",
  "accept": "image/*",
  "onUpload": { "behavior": "INVOKE", "handler": "previewImage" } }
```

</TabItem>
</Tabs>

## Notes

**`UPLOAD` is its own behaviour.** It does not send the JSON payload an ordinary
trigger would. The bus builds a `FormData`, appends every selected file under
`name` (falling back to the node id, then to `"files"`), POSTs it as
`multipart/form-data`, and applies the response exactly like `APPLY_RESPONSE` —
so the server answers with a `UiPage` or a `UiPatch`.

**`INVOKE` gets the raw files.** A client handler receives them on
`ctx.files` as browser `File` objects, which is enough for a preview, a size
check or a fully offline demo. Return a patch to fold the result back into the
page, or return nothing if the handler already updated things itself.

**Upload fires on arrival, not on submit.** The trigger runs on `drop` and on
the hidden input's `change`. An upload zone sitting inside a
[`form`](./form.md) is therefore independent of that form's Save button — do
not expect the files to travel with the form's JSON payload.

**`accept` filters the picker, it does not validate.** The browser's dialog
honours it, drag-and-drop largely does not, and neither is a security boundary.
Check type and size on the server and answer with a patch that sets the error.

**Feedback is yours to send.** The node has no built-in progress bar or file
list. Render one by returning a patch that replaces a neighbouring node — a
[`list`](./list.md) of uploaded files, a [`progress`](./progress.md), or a
plain [`text`](./text.md) as in the specimen above.

## See also

- **[`field`](./field.md)** — `FieldType.FILE` for a single inline picker.
- **[`form`](./form.md)** — the surrounding container.
- **[`fieldgroup`](./fieldgroup.md)** — grouping an upload with related inputs.
- **[Forms](../forms.md)** — the wider form story.
- **[Triggers & actions](../triggers.md)** — the `UPLOAD` and `INVOKE` behaviours.
