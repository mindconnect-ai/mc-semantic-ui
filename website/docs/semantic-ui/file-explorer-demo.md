---
title: File explorer demo
sidebar_position: 13
---

# File explorer demo

`demo/mc-sui-file-explorer-demo` is a file explorer over a **sandboxed folder
on the real filesystem**, and the end-to-end showcase for the
[`UiUpload`](./triggers.md#upload--send-files) drop zone: browse directories,
download and delete files, create folders, and **upload by dragging files onto
the drop zone**.

It is a server-driven Spring Boot app (no database). Every listing and mutation
returns a `UiPage`; the app runs in SPA mode so the `SuiEventBus` handles
drag-and-drop uploads and in-place navigation.

## Run it

```bash
mvn -f semantic-ui/core/mc-semantic-ui-core/pom.xml install -DskipTests
mvn -f semantic-ui/demo/mc-sui-file-explorer-demo/pom.xml spring-boot:run
# then open http://localhost:9092
```

A sandbox folder (`explorer-root/`, configurable via `explorer.root`) is created
and seeded with sample content on first start. Everything you do stays inside it.

## What it shows

- **`UiUpload`** → `UiTrigger.upload(url)`: the drop zone POSTs the files as
  `multipart/form-data` to the current folder, which then re-renders with a
  toast. The multipart part name is the node's `name` (`"files"` here).
- Directory navigation via a breadcrumb and click-to-open folders, all through
  `UiTrigger.go(...)`.
- Click-to-download files with `UiTrigger.download(...)` — the server streams
  the bytes with a `Content-Disposition` header, the bus turns that into a save
  dialog.
- Delete (with confirm) and a "new folder" form (`UiForm` → JSON `POST`) with
  **server-side validation**: an empty, invalid, or duplicate name comes back as
  a field error (`UiField.error(...)`) with the typed value preserved — see
  [Forms & validation](./forms.md#validation).
- **Path-traversal sandboxing** in `FileStorageService`: every path is resolved
  under one root and anything that escapes it is rejected with a 400.

## The upload, end to end

```java
// UI: the drop zone targets the current folder.
UiUpload.of("upload", "Upload to this folder")
        .name("files").multiple()
        .uploadTo("/files/upload?path=" + encodedPath);   // UiTrigger.upload(...)
```

```java
// Controller: a normal multipart handler, returning the next tree.
@PostMapping("/files/upload")
public UiPage upload(@RequestParam String path,
                     @RequestParam("files") MultipartFile[] files) {
    for (var f : files) storage.store(path, f);
    return new ExplorerPage(path, storage.list(path)).render()
            .toast(UiToast.success(files.length + " files uploaded."));
}
```

No client code beyond the drop zone: the `SuiEventBus` collects the dropped
`File` objects, POSTs them, and applies the returned `UiPage`.
