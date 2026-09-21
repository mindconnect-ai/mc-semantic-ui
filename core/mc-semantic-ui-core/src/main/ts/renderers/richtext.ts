import { renderIcon } from "./icon.js";

/**
 * The RICHTEXT field: an editable area with a small formatting toolbar, and
 * a hidden input that carries the area's HTML for the form.
 *
 * <p>Three jobs, all of them here so the field's markup stays declarative:
 * <ul>
 *   <li>{@link renderRichTextToolbar} — the toolbar's markup, shared by the
 *       browser renderer (renderers/field.ts) and mirrored in field.hbs;</li>
 *   <li>{@link sanitizeRichText} — what a value and a paste are reduced to:
 *       the same small vocabulary the toolbar produces, and nothing that runs
 *       or styles;</li>
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
 * A URL as a browser resolves its scheme: every control character and space
 * removed — a browser drops tabs and newlines anywhere in a URL, so
 * `jav\tascript:` is `javascript:` to it — and lower-cased.
 */
function schemeView(url: string): string {
    return url.replace(/[\u0000-\u0020\u007f-\u009f]/g, "").toLowerCase();
}

/**
 * Whether a link target may be kept: http(s), mailto, tel, or a relative
 * path — never a scheme that runs something, however it is spelled.
 */
export function safeHref(href: string): boolean {
    const v = schemeView(href);
    if (v === "") return false;
    if (/^(https?:|mailto:|tel:)/.test(v)) return true;
    if (/^[a-z][a-z0-9+.-]*:/.test(v)) return false;   // any other scheme
    return true;                                       // relative, or protocol-relative
}

/**
 * Whether an image source may be kept: an image embedded as data, or one
 * fetched over http(s) — never a script scheme, never SVG, nothing else inline.
 */
export function safeImageSrc(src: string): boolean {
    const v = schemeView(src);
    return /^data:image\/(png|jpeg|jpg|gif|webp|bmp);base64,/.test(v) || /^https?:\/\//.test(v);
}

// The sanitiser below works on the string, not on a DOM, so it runs the same
// in a browser, in Node and — as RichTextSanitizer.java — on the server: one
// policy, three places, held together by src/test/resources/richtext/
// sanitize-cases.json, which both test suites run. It is safe by
// construction: the output is rebuilt from scratch — tags from the allowlist
// with checked attributes, written by this code, and every other character
// of the input as escaped text. Nothing of the input reaches a tag or an
// attribute position unexamined, so a parser quirk cannot turn text into
// markup.

/** Elements removed with everything in them: their content is code, style, or not text the user meant. */
const RAW_CONTENT = /<(script|style|head|title|template|noscript|iframe|object|embed|applet|svg|math|form|button|textarea|select|option|xmp|noembed|noframes|plaintext)(?![a-zA-Z0-9:-])[\s\S]*?(?:<\/\1[ \t\n\f\r]*>|(?![\s\S]))/gi;
/** A tag, opening or closing, with its attributes. */
const TAG = /<(\/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:[ \t\n\f\r]+[^ \t\n\f\r"'>\/=]+(?:[ \t\n\f\r]*=[ \t\n\f\r]*(?:"[^"]*"|'[^']*'|[^ \t\n\f\r"'>]+))?)*)[ \t\n\f\r]*\/?>/g;
/** One attribute inside a tag's attribute text. */
const ATTR = /([^ \t\n\f\r"'>\/=]+)(?:[ \t\n\f\r]*=[ \t\n\f\r]*(?:"([^"]*)"|'([^']*)'|([^ \t\n\f\r"'>]+)))?/g;
const VOID_TAGS = new Set(["br", "hr", "img"]);

/** The named entities an attribute value is decoded from — enough to spell any scheme a browser would run. */
const NAMED_ENTITIES: Record<string, string> = {
    amp: "&", lt: "<", gt: ">", quot: "\"", apos: "'", nbsp: "\u00a0",
    colon: ":", period: ".", plus: "+", sol: "/", num: "#", excl: "!", quest: "?",
    equals: "=", lpar: "(", rpar: ")", Tab: "\t", NewLine: "\n",
};

/**
 * An attribute value with its character references resolved. What is
 * checked is this decoded value, and what is written is this decoded value
 * escaped afresh — so whatever a browser would have made of the original
 * spelling, what it reads is exactly what was checked.
 */
export function decodeEntities(value: string): string {
    return value.replace(/&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{0,31});?/g, (all, ent: string) => {
        if (ent[0] === "#") {
            const cp = ent[1] === "x" || ent[1] === "X" ? parseInt(ent.slice(2), 16) : parseInt(ent.slice(1), 10);
            return cp > 0 && cp <= 0x10ffff && !(cp >= 0xd800 && cp <= 0xdfff) ? String.fromCodePoint(cp) : "\ufffd";
        }
        return Object.prototype.hasOwnProperty.call(NAMED_ENTITIES, ent) ? NAMED_ENTITIES[ent]! : all;
    });
}

function escapeAttr(value: string): string {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/** Text between tags: `<` and `>` escaped, a stray `&` too; a well-formed entity is kept as the text it stands for. */
function escapeText(text: string): string {
    return text.replace(/&(?!(?:#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{0,31});)/g, "&amp;")
        .replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** The attributes an allowed tag keeps, rebuilt: ` name="value"`, checked and escaped. */
function keptAttributes(tag: string, attrText: string): string | null {
    let out = "";
    const seen = new Set<string>();
    let hasHref = false, hasSrc = false;
    ATTR.lastIndex = 0;
    for (let m = ATTR.exec(attrText); m; m = ATTR.exec(attrText)) {
        const name = m[1]!.toLowerCase();
        if (seen.has(name)) continue;            // a browser keeps the first; so does this
        seen.add(name);
        const value = decodeEntities(m[2] ?? m[3] ?? m[4] ?? "");
        const keep = (tag === "a" && name === "href" && safeHref(value))
            || (tag === "img" && name === "src" && safeImageSrc(value))
            || (tag === "img" && name === "alt")
            || (tag === "img" && (name === "width" || name === "height") && /^[0-9]{1,5}$/.test(value));
        if (!keep) continue;
        if (name === "href") hasHref = true;
        if (name === "src") hasSrc = true;
        out += ` ${name}="${escapeAttr(value)}"`;
    }
    // A link with nowhere to go is only its text; an image with no source is nothing.
    if ((tag === "a" && !hasHref) || (tag === "img" && !hasSrc)) return null;
    return out;
}

/**
 * Reduces HTML to what the toolbar itself produces: paragraphs, line breaks,
 * bold, italic, underline, lists, quotes, headings, code, links to http(s),
 * mail or phone, and images embedded as data or fetched over http(s).
 * Script, style and embedded documents go with their content, other tags are
 * unwrapped (their text stays), and no attribute survives but a checked
 * `href`, `src`, `alt`, `width` or `height`. Used on what is pasted, and on
 * every value a RICHTEXT field renders.
 */
export function sanitizeRichText(html: string | null | undefined): string {
    if (html == null || html === "") return "";
    const input = String(html)
        .replace(/<!--[\s\S]*?(?:-->|(?![\s\S]))/g, "")
        .replace(/<[!?][\s\S]*?>/g, "")
        .replace(RAW_CONTENT, "");
    let out = "";
    let last = 0;
    const anchors: boolean[] = [];               // per open <a>: was it written?
    TAG.lastIndex = 0;
    for (let m = TAG.exec(input); m; m = TAG.exec(input)) {
        out += escapeText(input.slice(last, m.index));
        last = m.index + m[0].length;
        const closing = m[1] === "/";
        const tag = m[2]!.toLowerCase();
        if (!allowedTag(tag)) continue;
        if (closing) {
            if (VOID_TAGS.has(tag)) continue;
            if (tag === "a" && !anchors.pop()) continue;
            out += `</${tag}>`;
            continue;
        }
        const attrs = keptAttributes(tag, m[3] ?? "");
        if (tag === "a") anchors.push(attrs !== null);
        if (attrs === null) continue;
        out += `<${tag}${attrs}>`;
    }
    return out + escapeText(input.slice(last));
}

/** Earlier name of {@link sanitizeRichText}, kept for callers of the paste hook. */
export const sanitizeHtml = sanitizeRichText;

/** Plain text as HTML: paragraphs for blank lines, breaks for the rest. */
function textToHtml(text: string): string {
    const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return text.replace(/\r\n?/g, "\n").split(/\n{2,}/)
        .map(p => `<p>${esc(p).replace(/\n/g, "<br>")}</p>`).join("");
}

/** The longest side an embedded image is scaled down to, in pixels. */
export const MAX_IMAGE_SIDE = 1280;

/** A file's own bytes as a `data:` URL. */
function readAsDataUrl(file: File): Promise<string | null> {
    return new Promise(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : null);
        reader.onerror = () => resolve(null);
        reader.readAsDataURL(file);
    });
}

/**
 * An image file as a `data:` URL. One that is already at most
 * {@link MAX_IMAGE_SIDE} on its longer side goes in as it is — an animated
 * GIF keeps its animation, a transparent one its transparency. A larger one
 * is scaled down: a JPEG (or BMP) stays JPEG, anything that may be
 * transparent becomes PNG. Null when the browser cannot decode it.
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
        if (Math.max(img.naturalWidth, img.naturalHeight) <= MAX_IMAGE_SIDE) {
            const original = await readAsDataUrl(file);
            if (original && safeImageSrc(original)) return original;
        }
        const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(img.naturalWidth, img.naturalHeight, 1));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
        const ctx = canvas.getContext("2d");
        if (!ctx) return null;
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const opaque = file.type === "image/jpeg" || file.type === "image/bmp";
        return opaque ? canvas.toDataURL("image/jpeg", 0.85) : canvas.toDataURL("image/png");
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
                const clean = sanitizeRichText(served);   // the renderer cleaned it; this makes sure
                if (editor.innerHTML !== clean) editor.innerHTML = clean;
                input.value = clean;
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
        const clean = html ? sanitizeRichText(html) : textToHtml(text);
        document.execCommand("insertHTML", false, clean);
        sync();
    });
    editor.addEventListener("keyup", () => reflect(box, editor));
    editor.addEventListener("mouseup", () => reflect(box, editor));
    installImageTools(box, editor, sync);

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

// ── Images: resize by a handle, move by dragging ─────────────────────────────

/** The smallest width an image can be dragged to, in pixels. */
const MIN_IMAGE_WIDTH = 40;

/**
 * What re-places each field's image frame when the window resizes. One
 * window listener serves them all, and a field whose box has left the page
 * takes itself out — so a form re-rendered a hundred times does not leave a
 * hundred listeners, each holding a detached editor, behind.
 */
const resizeHooks = new Set<() => void>();
let resizeWired = false;
function onWindowResize(hook: () => void): void {
    resizeHooks.add(hook);
    if (resizeWired || typeof window === "undefined") return;
    resizeWired = true;
    window.addEventListener("resize", () => resizeHooks.forEach(h => h()));
}

/**
 * A click on an image selects it and shows a frame with a handle at its
 * bottom-right corner; dragging the handle scales the image, proportionally,
 * and writes the result as a `width` attribute — plain HTML, no style, so it
 * survives the sanitiser and shows the same anywhere. The frame is an overlay
 * beside the editor, not inside it, so nothing of it lands in the value.
 * Moving an image is the browser's own: drag it to where it should go.
 */
function installImageTools(box: HTMLElement, editor: HTMLElement, sync: () => void): void {
    const frame = document.createElement("div");
    frame.className = "sui-richtext-imgframe";
    frame.hidden = true;
    const handle = document.createElement("div");
    handle.className = "sui-richtext-imghandle";
    handle.title = "Drag to resize";
    frame.appendChild(handle);
    box.appendChild(frame);

    let selected: HTMLImageElement | null = null;

    const place = (): void => {
        if (!selected || !selected.isConnected || !editor.contains(selected)) { hide(); return; }
        const b = box.getBoundingClientRect(), r = selected.getBoundingClientRect();
        frame.style.left = `${r.left - b.left + box.scrollLeft}px`;
        frame.style.top = `${r.top - b.top + box.scrollTop}px`;
        frame.style.width = `${r.width}px`;
        frame.style.height = `${r.height}px`;
        frame.hidden = false;
    };
    const hide = (): void => { selected = null; frame.hidden = true; };
    const select = (img: HTMLImageElement): void => {
        selected = img;
        // The browser's selection on the image too, so Delete removes it and
        // a drag moves it.
        const range = document.createRange();
        range.selectNode(img);
        const sel = window.getSelection();
        sel?.removeAllRanges();
        sel?.addRange(range);
        place();
    };

    editor.addEventListener("click", e => {
        const t = e.target;
        if (t instanceof HTMLImageElement) select(t); else hide();
    });
    editor.addEventListener("input", () => { if (selected) place(); });
    editor.addEventListener("keydown", e => { if (e.key === "Escape") hide(); });
    editor.addEventListener("scroll", () => { if (selected) place(); });
    editor.addEventListener("blur", () => {
        // Not when the handle took the focus: that is a resize starting.
        setTimeout(() => { if (!dragging) hide(); }, 0);
    });
    const reposition = (): void => {
        if (!box.isConnected) { resizeHooks.delete(reposition); return; }
        if (selected) place();
    };
    onWindowResize(reposition);

    let dragging = false;
    handle.addEventListener("mousedown", e => {
        if (!selected) return;
        e.preventDefault();
        e.stopPropagation();
        const img = selected;
        const startX = e.clientX, startWidth = img.getBoundingClientRect().width;
        const ratio = img.naturalWidth > 0 ? img.naturalHeight / img.naturalWidth : 0;
        const maxWidth = editor.clientWidth - 2;
        dragging = true;
        const move = (ev: MouseEvent): void => {
            const width = Math.round(Math.min(maxWidth, Math.max(MIN_IMAGE_WIDTH, startWidth + (ev.clientX - startX))));
            img.setAttribute("width", String(width));
            if (ratio > 0) img.setAttribute("height", String(Math.round(width * ratio)));
            place();
        };
        const up = (): void => {
            dragging = false;
            document.removeEventListener("mousemove", move);
            document.removeEventListener("mouseup", up);
            sync();
            editor.focus();
            select(img);
        };
        document.addEventListener("mousemove", move);
        document.addEventListener("mouseup", up);
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
