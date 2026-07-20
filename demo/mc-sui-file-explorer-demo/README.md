# mc-sui-file-explorer-demo

A file explorer over a **sandboxed folder on the real filesystem**, rendered
with Semantic UI. Browse directories, download and delete files, create
folders, and **upload files by dragging them onto the drop zone** — the
end-to-end showcase for the `UiUpload` node and the `UPLOAD` trigger.

It is a server-driven Spring Boot app (no database). Every listing and mutation
returns a `UiPage`; the page runs in SPA mode so the `SuiEventBus` handles
drag-and-drop uploads and in-place navigation.

## What it demonstrates

- **`UiUpload`** drop zone → multipart upload to the current folder, then the
  folder re-renders with a toast.
- Directory navigation via a breadcrumb and click-to-open folders.
- Click-to-download files (`UiTrigger.download`, streamed with a
  `Content-Disposition` header).
- Delete (with confirm) and a "new folder" form (`UiForm` → JSON `POST`).
- **Path-traversal sandboxing** in `FileStorageService`: every path is resolved
  under one root and anything escaping it is rejected.

## Run it

```bash
mvn -f semantic-ui/core/mc-semantic-ui-core/pom.xml install -DskipTests   # build core first
mvn -f semantic-ui/demo/mc-sui-file-explorer-demo/pom.xml spring-boot:run
# then open http://localhost:9092
```

On first start a sandbox folder (`explorer-root/`, configurable via
`explorer.root`) is created next to the working directory and seeded with a few
sample files and folders. Everything you do in the UI happens inside it.

## Endpoints

| Route             | Method | Purpose                                  |
|-------------------|--------|------------------------------------------|
| `/files`          | GET    | Directory listing (`?path=` relative)    |
| `/files/upload`   | POST   | Multipart upload into `?path=`           |
| `/files/download` | GET    | Stream a file (`?path=` to the file)     |
| `/files/delete`   | POST   | Delete a file/folder at `?path=`         |
| `/files/mkdir`    | POST   | Create a folder in `?path=` (JSON body)  |
