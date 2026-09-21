import { renderIcon } from "./icon.js";

/**
 * The RICHTEXT field: an editable area with a small formatting toolbar, and
 * a hidden input that carries the area's HTML for the form.
 *
 * <p>Three jobs, all of them here so the field's markup stays declarative:
 * <ul>
 *   <li>{@link renderRichTextToolbar} — the toolbar's markup, shared by the
 *       browser renderer (renderers/field.ts) and mirrored in field.hbs;</li>
 *   <li>{@link sanitizeHtml} — what a paste is reduced to: the same small
 *       vocabulary the toolbar produces, and nothing that runs or styles;</li>
 *   <li>{@link wireRichText} — the behaviour, run by the event bus after
 *       every render: the toolbar drives the editor, every edit lands in the
 *       hidden input, and a value the server changed lands in the editor.</li>
 * </ul>
 */

/** The toolbar's buttons, in order: command name, icon token, label. */
export const RICHTEXT_COMMANDS: ReadonlyArray<readonly [string, string, string]> = [
    ["bold", "bold", "Bold"],
    ["italic", "italic", "Italic"],
    ["underline", "underline", "Underline"],
    ["bullets", "list", "Bulleted list"],
    ["numbers", "list-ordered", "Numbered list"],
    ["link", "link", "Link"],
    ["quote", "text-quote", "Quote"],
    ["clear", "remove-formatting", "Remove formatting"],
];

/** The toolbar's markup — one button per {@link RICHTEXT_COMMANDS} entry. */
export function renderRichTextToolbar(): string {
    const buttons = RICHTEXT_COMMANDS.map(([cmd, icon, label]) =>
        `<button type="button" class="sui-richtext-btn" data-sui-richtext-cmd="${cmd}" aria-label="${label}" title="${label}">${renderIcon(icon)}</button>`).join("");
    return `<div class="sui-richtext-toolbar" role="toolbar" aria-label="Formatting">${buttons}</div>`;
}

// ── Sanitising ──────────────────────────────────────────────────────────────

/** Tags a paste may keep. Everything else is unwrapped — its text stays, the tag goes. */
const ALLOWED_TAGS = new Set([
    "p", "br", "div", "span",
    "b", "strong", "i", "em", "u", "s", "strike", "sub", "sup", "code", "pre",
    "ul", "ol", "li", "blockquote", "a", "hr", "img",
    "h1", "h2", "h3", "h4", "h5", "h6",
]);

/** Tags a paste drops with their whole content: nothing in them is text the user meant. */
const DROPPED_TAGS = new Set([
    "script", "style", "link", "meta", "head", "title", "base", "template", "noscript",
    "iframe", "frame", "object", "embed", "applet", "svg", "math",
    "form", "input", "button", "textarea", "select", "option",
]);

/** Whether a tag survives a paste. */
export function allowedTag(tag: string): boolean { return ALLOWED_TAGS.has(tag.toLowerCase()); }

/** Whether a tag is removed with its content. */
export function droppedTag(tag: string): boolean { return DROPPED_TAGS.has(tag.toLowerCase()); }

/**
 * Whether a link target may be kept: http(s), mailto, tel, or a relative
 * path — never a scheme that runs something.
 */
export function safeHref(href: string): boolean {
    const v = href.trim().toLowerCase();
    if (v === "") return false;
    if (/^(https?:|mailto:|tel:)/.test(v)) return true;
    if (/^[a-z][a-z0-9+.-]*:/.test(v)) return false;   // any other scheme
    return true;                                       // relative, or protocol-relative
}

/**
 * Whether an image source may be kept: an image embedded as data, or one
 * fetched over http(s) — never a script scheme, never anything else inline.
 */
export function safeImageSrc(src: string): boolean {
    const v = src.trim().toLowerCase();
    return /^data:image\/(png|jpeg|jpg|gif|webp|bmp);base64,/.test(v) || /^https?:\/\//.test(v);
}

/**
 * Reduces HTML to what the toolbar itself can produce. Elements that run or
 * style are dropped with their content, unknown ones are unwrapped, and no
 * attribute survives but a safe `href` on a link. Parsed by the browser's own
 * inert parser, so nothing here executes on the way through.
 */
export function sanitizeHtml(html: string): string {
    const doc = new DOMParser().parseFromString(`<!doctype html><body>${html}`, "text/html");
    clean(doc.body);
    return doc.body.innerHTML;
}

function clean(parent: Element): void {
    for (const child of Array.from(parent.childNodes)) {
        if (child.nodeType === Node.COMMENT_NODE) { child.remove(); continue; }
        if (child.nodeType !== Node.ELEMENT_NODE) continue;
        const el = child as Element;
        const tag = el.tagName.toLowerCase();
        if (droppedTag(tag)) { el.remove(); continue; }
        clean(el);
        if (!allowedTag(tag)) {
            // Unwrap: the children take the element's place.
            while (el.firstChild) parent.insertBefore(el.firstChild, el);
            el.remove();
            continue;
        }
        for (const attr of Array.from(el.attributes)) {
            const keep = (tag === "a" && attr.name === "href" && safeHref(attr.value))
                || (tag === "img" && attr.name === "src" && safeImageSrc(attr.value))
                || (tag === "img" && attr.name === "alt");
            if (!keep) el.removeAttribute(attr.name);
        }
        // A link left with nowhere to go is only its text; an image with no
        // source is nothing.
        if (tag === "a" && !el.hasAttribute("href")) {
            while (el.firstChild) parent.insertBefore(el.firstChild, el);
            el.remove();
        } else if (tag === "img" && !el.hasAttribute("src")) {
            el.remove();
        }
    }
}

/** Plain text as HTML: paragraphs for blank lines, breaks for the rest. */
function textToHtml(text: string): string {
    const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return text.replace(/\r\n?/g, "\n").split(/\n{2,}/)
        .map(p => `<p>${esc(p).replace(/\n/g, "<br>")}</p>`).join("");
}

/** The longest side an embedded image is scaled down to, in pixels. */
export const MAX_IMAGE_SIDE = 1280;

/**
 * An image file as a `data:` URL, scaled to at most {@link MAX_IMAGE_SIDE}
 * on its longer side. PNG stays PNG (it may be transparent); anything else
 * becomes JPEG. Null when the browser cannot decode it.
 */
export async function imageToDataUrl(file: File): Promise<string | null> {
    const url = URL.createObjectURL(file);
    try {
        const img = await new Promise<HTMLImageElement>((resolve, reject) => {
            const i = new Image();
            i.onload = () => resolve(i);
            i.onerror = () => reject(new Error("not an image"));
            i.src = url;
        });
        const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(img.naturalWidth, img.naturalHeight, 1));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
        const ctx = canvas.getContext("2d");
        if (!ctx) return null;
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        return file.type === "image/png" ? canvas.toDataURL("image/png") : canvas.toDataURL("image/jpeg", 0.85);
    } catch {
        return null;
    } finally {
        URL.revokeObjectURL(url);
    }
}

// ── Behaviour ───────────────────────────────────────────────────────────────

interface RichTextState {
    /** The value the server last sent — the hidden input's `value` attribute as of the last look. */
    served: string;
    /** The HTML when the editor took focus, to tell a real change on blur. */
    onFocus: string;
}

/**
 * Wires every RICHTEXT field under {@code root}. Idempotent — the event bus
 * runs it after every render; call it yourself when rendering without one.
 *
 * <p>On a field seen for the first time it installs the behaviour. On one
 * seen before it looks at the hidden input's {@code value} attribute: a
 * patch that merged a new {@code value} into the field re-rendered the
 * input with it, and the editor takes it over — whether or not the editor
 * has focus, which the morpher would otherwise leave alone.
 */
export function wireRichText(root: ParentNode = document): void {
    root.querySelectorAll<HTMLElement>("[data-sui-richtext]").forEach(box => {
        const editor = box.querySelector<HTMLElement>(".sui-richtext-editor");
        const input = box.querySelector<HTMLInputElement>('input[type="hidden"]');
        if (!editor || !input) return;
        const served = input.getAttribute("value") ?? "";
        const state = (box as unknown as { __suiRichText?: RichTextState }).__suiRichText;
        if (state) {
            if (state.served !== served) {
                state.served = served;
                if (editor.innerHTML !== served) editor.innerHTML = served;
                input.value = served;
            }
            return;
        }
        (box as unknown as { __suiRichText?: RichTextState }).__suiRichText = { served, onFocus: editor.innerHTML };
        input.value = editor.innerHTML;
        install(box, editor, input);
    });
}

function install(box: HTMLElement, editor: HTMLElement, input: HTMLInputElement): void {
    const state = (box as unknown as { __suiRichText: RichTextState }).__suiRichText;
    const sync = (): void => { input.value = editor.innerHTML; };

    editor.addEventListener("input", () => { sync(); reflect(box, editor); });
    editor.addEventListener("focus", () => { state.onFocus = editor.innerHTML; });
    // A textarea reports a change when it loses focus; so does this.
    editor.addEventListener("blur", () => {
        sync();
        if (editor.innerHTML !== state.onFocus) {
            state.onFocus = editor.innerHTML;
            input.dispatchEvent(new Event("change", { bubbles: true }));
        }
    });
    editor.addEventListener("paste", e => {
        const data = e.clipboardData;
        if (!data) return;
        e.preventDefault();
        // An image on the clipboard — a screenshot, a copied picture — goes in
        // as itself, embedded in the HTML as data. Sized down first: a
        // screenshot of a retina screen is megabytes nobody wants in a form.
        const images = Array.from(data.files ?? []).filter(f => f.type.startsWith("image/"));
        if (images.length > 0) {
            void (async () => {
                for (const file of images) {
                    const src = await imageToDataUrl(file);
                    if (src) document.execCommand("insertHTML", false, `<img src="${src}" alt="">`);
                }
                sync();
            })();
            return;
        }
        const html = data.getData("text/html");
        const text = data.getData("text/plain");
        const clean = html ? sanitizeHtml(html) : textToHtml(text);
        document.execCommand("insertHTML", false, clean);
        sync();
    });
    editor.addEventListener("keyup", () => reflect(box, editor));
    editor.addEventListener("mouseup", () => reflect(box, editor));

    const toolbar = box.querySelector<HTMLElement>(".sui-richtext-toolbar");
    if (!toolbar) return;
    // A press on a button must not take the selection away from the editor.
    toolbar.addEventListener("mousedown", e => e.preventDefault());
    toolbar.addEventListener("click", e => {
        const btn = (e.target as Element | null)?.closest<HTMLElement>("[data-sui-richtext-cmd]");
        if (!btn) return;
        e.preventDefault();
        editor.focus();
        run(btn.dataset.suiRichtextCmd ?? "", editor);
        sync();
        reflect(box, editor);
    });
}

/** Applies a toolbar command to the editor's current selection. */
function run(cmd: string, editor: HTMLElement): void {
    switch (cmd) {
        case "bold": document.execCommand("bold"); break;
        case "italic": document.execCommand("italic"); break;
        case "underline": document.execCommand("underline"); break;
        case "bullets": document.execCommand("insertUnorderedList"); break;
        case "numbers": document.execCommand("insertOrderedList"); break;
        case "link": {
            const current = enclosing(editor, "a") as HTMLAnchorElement | null;
            const url = window.prompt("Link URL", current?.getAttribute("href") ?? "https://");
            if (url === null) return;
            if (url.trim() === "" || !safeHref(url)) { document.execCommand("unlink"); return; }
            document.execCommand("createLink", false, url.trim());
            break;
        }
        case "quote":
            document.execCommand("formatBlock", false, enclosing(editor, "blockquote") ? "p" : "blockquote");
            break;
        case "clear":
            document.execCommand("removeFormat");
            document.execCommand("unlink");
            if (enclosing(editor, "blockquote")) document.execCommand("formatBlock", false, "p");
            break;
    }
}

/** The element of that tag around the selection, inside the editor, if any. */
function enclosing(editor: HTMLElement, tag: string): Element | null {
    const sel = window.getSelection();
    const node = sel?.anchorNode;
    if (!node) return null;
    const start = node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement;
    const found = start?.closest(tag) ?? null;
    return found && editor.contains(found) && found !== editor ? found : null;
}

/** Lights the buttons whose formatting the selection is in. */
function reflect(box: HTMLElement, editor: HTMLElement): void {
    const active: Record<string, boolean> = {
        bold: document.queryCommandState("bold"),
        italic: document.queryCommandState("italic"),
        underline: document.queryCommandState("underline"),
        bullets: document.queryCommandState("insertUnorderedList"),
        numbers: document.queryCommandState("insertOrderedList"),
        link: !!enclosing(editor, "a"),
        quote: !!enclosing(editor, "blockquote"),
    };
    box.querySelectorAll<HTMLElement>("[data-sui-richtext-cmd]").forEach(btn => {
        btn.classList.toggle("is-active", !!active[btn.dataset.suiRichtextCmd ?? ""]);
    });
}
