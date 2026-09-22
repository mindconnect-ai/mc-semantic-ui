/**
 * Just enough DOM to push a real click through SuiEventBus: elements with
 * parents, attributes, classes and dataset, and closest / matches /
 * querySelectorAll over simple selectors — tag, #id, .class, [attr],
 * [attr='v'], descendant and child combinators, comma lists. A selector with
 * a pseudo-class this does not know matches nothing, which keeps the bus off
 * paths the test does not build. No jsdom, like the other TypeScript tests.
 */
export class El {
    constructor(tag, attrs = {}, children = []) {
        this.tagName = tag.toUpperCase();
        this.attrs = { ...attrs };
        this.children = [];
        this.parentElement = null;
        this.listeners = {};
        this.style = { setProperty(k, v) { this[k] = v; }, getPropertyValue(k) { return this[k] ?? ""; }, removeProperty(k) { delete this[k]; } };
        for (const c of children) this.appendChild(c);
    }
    get id() { return this.attrs.id ?? ""; }
    set id(v) { this.attrs.id = v; }
    get className() { return this.attrs.class ?? ""; }
    set className(v) { this.attrs.class = v; }
    get classList() {
        const el = this;
        const list = () => (el.attrs.class ?? "").split(/\s+/).filter(Boolean);
        return {
            contains: c => list().includes(c),
            add: (...cs) => { el.attrs.class = [...new Set([...list(), ...cs])].join(" "); },
            remove: (...cs) => { el.attrs.class = list().filter(c => !cs.includes(c)).join(" "); },
            toggle: (c, force) => { const on = force ?? !list().includes(c); on ? this.classList.add(c) : this.classList.remove(c); return on; },
        };
    }
    get dataset() {
        const out = {};
        for (const [k, v] of Object.entries(this.attrs)) {
            if (k.startsWith("data-")) out[k.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = v;
        }
        return out;
    }
    get isConnected() { return true; }
    getAttribute(n) { return n in this.attrs ? String(this.attrs[n]) : null; }
    setAttribute(n, v) { this.attrs[n] = String(v); }
    removeAttribute(n) { delete this.attrs[n]; }
    hasAttribute(n) { return n in this.attrs; }
    appendChild(c) { c.parentElement = this; this.children.push(c); return c; }
    remove() {
        const p = this.parentElement;
        if (!p) return;
        p.children = p.children.filter(c => c !== this);
        this.parentElement = null;
    }
    addEventListener(type, fn) { (this.listeners[type] ??= []).push(fn); }
    removeEventListener() { }
    /** Calls the listeners on this element and, for a bubbling event, on its ancestors. */
    dispatchEvent(event) {
        if (event.target == null) event.target = this;
        for (let e = this; e; e = e.parentElement) {
            for (const fn of [...(e.listeners[event.type] ?? [])]) fn(event);
            if (!event.bubbles) break;
        }
        return !event.defaultPrevented;
    }
    /** Not a parser: the markup is remembered, which is all the renderer's morph needs here. */
    get innerHTML() { return this._html ?? ""; }
    set innerHTML(v) { this._html = String(v); }
    get outerHTML() { return this._outerHtml ?? ""; }
    set outerHTML(v) { this._outerHtml = String(v); }
    contains(el) { for (let e = el; e; e = e.parentElement) if (e === this) return true; return false; }
    closest(sel) { for (let e = this; e; e = e.parentElement) if (e.matches(sel)) return e; return null; }
    matches(sel) { return sel.split(",").some(s => matchComplex(this, s.trim())); }
    querySelectorAll(sel) {
        const out = [];
        const parts = sel.split(",").map(s => s.trim());
        const hit = (c) => parts.some(s => s.startsWith(":scope")
            ? matchComplex(c, s.slice(":scope".length).trim(), this)
            : matchComplex(c, s));
        const walk = (e) => { for (const c of e.children) { if (hit(c)) out.push(c); walk(c); } };
        walk(this);
        return out;
    }
    querySelector(sel) { return this.querySelectorAll(sel)[0] ?? null; }
    getBoundingClientRect() { return { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }; }
    focus() { }
    get textContent() { return ""; }
}

/** "A B > C": the last compound matches the element, the rest its ancestors. */
function matchComplex(el, selector, scope = null) {
    const parts = selector.replace(/\s*>\s*/g, " > ").split(/\s+/).filter(Boolean);
    const walk = (node, i) => {
        if (!matchCompound(node, parts[i])) return false;
        if (i === 0) return true;
        // ":scope > a": the leading ">" ties the first compound to the scope element.
        if (i === 1 && parts[0] === ">") return node.parentElement === scope;
        if (parts[i - 1] === ">") return !!node.parentElement && walk(node.parentElement, i - 2);
        for (let a = node.parentElement; a; a = a.parentElement) if (walk(a, i - 1)) return true;
        return false;
    };
    return parts.length > 0 && walk(el, parts.length - 1);
}

function matchCompound(el, compound) {
    if (!compound || compound === ">") return false;
    const re = /^([a-zA-Z][\w-]*)|#([\w-]+)|\.([\w-]+)|\[([\w-]+)(?:=(['"]?)(.*?)\5)?\]|(:.+)/g;
    let m, consumed = 0;
    while ((m = re.exec(compound)) && m[0] !== "") {
        if (m.index !== consumed) return false;
        consumed = m.index + m[0].length;
        if (m[1] && el.tagName !== m[1].toUpperCase()) return false;
        if (m[2] && el.id !== m[2]) return false;
        if (m[3] && !el.classList.contains(m[3])) return false;
        if (m[4]) {
            if (!(m[4] in el.attrs)) return false;
            if (m[6] !== undefined && m[0].includes("=") && String(el.attrs[m[4]]) !== m[6]) return false;
        }
        if (m[7]) return false;   // pseudo-classes: not modelled
    }
    return consumed === compound.length;
}

/** An <input>/<textarea> the bus's harvest recognises. */
export function installControls() {
    globalThis.HTMLElement = El;
    globalThis.Element = El;
    globalThis.Event = class {
        constructor(type, init = {}) {
            this.type = type;
            this.bubbles = !!init.bubbles;
            this.target = null;
            this.defaultPrevented = false;
        }
        preventDefault() { this.defaultPrevented = true; }
    };
    globalThis.CustomEvent = class extends globalThis.Event {
        constructor(type, init = {}) { super(type, init); this.detail = init.detail ?? null; }
    };
    globalThis.HTMLInputElement = class extends El {
        get name() { return this.attrs.name; }
        get type() { return this.attrs.type ?? "text"; }
        get value() { return this.attrs.value ?? ""; }
        set value(v) { this.attrs.value = String(v); }
        get checked() { return "checked" in this.attrs; }
        set checked(v) { if (v) this.attrs.checked = ""; else delete this.attrs.checked; }
    };
    globalThis.HTMLTextAreaElement = class extends El {
        get name() { return this.attrs.name; }
        get value() { return this.attrs.value ?? ""; }
        set value(v) { this.attrs.value = String(v); }
    };
    globalThis.HTMLSelectElement = class extends El { };
    // As in a DOM: a form's named getter shadows its own properties, so a form
    // holding <input name="name"> answers that element for `.name` instead of
    // a string. Modelled here because code that duck-types a control by its
    // `.name` gets it wrong in exactly this case.
    globalThis.HTMLFormElement = class extends El {
        get name() { return this.querySelector('[name="name"]') ?? this.attrs.name ?? ""; }
    };
    // As in a DOM: the open property is the open attribute.
    globalThis.HTMLDetailsElement = class extends El {
        get open() { return "open" in this.attrs; }
        set open(v) { if (v) this.attrs.open = ""; else delete this.attrs.open; }
    };
}
