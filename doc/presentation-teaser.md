---
marp: true
theme: default
paginate: true
size: 16:9
style: |
  /* ── Terminal theme: pure black background, green monospace everywhere.
        Same theme as the full presentation — kept in sync. */
  section:not(.diagram) {
    background: #000 !important;
    color: #00FF66 !important;
    font-family: 'Menlo', 'Consolas', 'Courier New', monospace !important;
    border-color: #00FF66 !important;
    font-size: 22px;
    padding: 40px 60px;
  }
  section:not(.diagram) p,
  section:not(.diagram) li,
  section:not(.diagram) span,
  section:not(.diagram) div,
  section:not(.diagram) table,
  section:not(.diagram) thead,
  section:not(.diagram) tbody,
  section:not(.diagram) tr,
  section:not(.diagram) th,
  section:not(.diagram) td,
  section:not(.diagram) code,
  section:not(.diagram) blockquote,
  section:not(.diagram) a,
  section:not(.diagram) strong,
  section:not(.diagram) em {
    background: #000 !important;
    color: #00FF66 !important;
    font-family: 'Menlo', 'Consolas', 'Courier New', monospace !important;
    border-color: #00FF66 !important;
  }
  h1 {
    color: #00FF66 !important;
    border-bottom: 2px solid #00FF66 !important;
    padding-bottom: 8px;
  }
  h2 {
    color: #00FF66 !important;
  }
  strong { font-weight: 700; }

  /* ── Code blocks: syntax-coloured ─────────────────────── */
  pre {
    background: #000 !important;
    color: #C0C0C0 !important;
    font-size: 0.7em;
    line-height: 1.4;
    padding: 16px;
    border: 1px solid #00FF66 !important;
    font-family: 'Menlo', 'Consolas', monospace !important;
  }
  pre code {
    background: transparent !important;
    color: #C0C0C0 !important;
    padding: 0;
    font-family: inherit !important;
  }
  pre code .hljs-string,
  pre code .hljs-attr      { color: #FFD75F !important; }
  pre code .hljs-keyword,
  pre code .hljs-built_in,
  pre code .hljs-literal   { color: #FF6E6E !important; }
  pre code .hljs-tag,
  pre code .hljs-name,
  pre code .hljs-selector-tag { color: #87D7FF !important; }
  pre code .hljs-attribute,
  pre code .hljs-meta      { color: #FFAF5F !important; }
  pre code .hljs-comment   { color: #7F7F7F !important; font-style: italic; }
  pre code .hljs-number    { color: #FFAF5F !important; }
  pre code .hljs-title,
  pre code .hljs-function,
  pre code .hljs-title.function_ { color: #87FFAF !important; }
  pre code .hljs-type,
  pre code .hljs-class     { color: #FFD7AF !important; }
  pre code .hljs-variable,
  pre code .hljs-template-variable { color: #FF87D7 !important; }
  pre code .hljs-punctuation { color: #C0C0C0 !important; }

  blockquote {
    border-left: 4px solid #00FF66 !important;
    padding: 8px 16px;
    margin: 12px 0;
  }
  .columns {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
  }
  /* ── Diagram slides: kill the terminal background so Marp's
        background-image (the SVG injected by `![bg fit]`) is visible. */
  section.diagram {
    background: #FFFFFF !important;
  }
  section.diagram > * {
    background: transparent !important;
    color: #1F2D5C !important;
  }
---

<!-- _class: lead -->

# semantic-ui

Modern UIs without the modern complexity.

<br>

Same model renders to plain HTML, to a live SPA,
or through your own custom renderer.

<br>

**David Beisert**

---

# The reality — a checkbox in Angular

A child toggles a checkbox. Its value must reach the
grandparent and persist in a shared service.

<div class="columns">

<div>

### 4× event bubbling

```typescript
// 1. Leaf child
@Component({ template: `
  <input type="checkbox"
         [checked]="selected"
         (change)="toggled.emit($event.target.checked)">
`})
class CheckboxCell {
  @Input()  selected = false;
  @Output() toggled  = new EventEmitter<boolean>();
}

// 2. Row component
@Component({ template: `
  <checkbox-cell [selected]="row.selected"
                 (toggled)="onToggle($event)">
  </checkbox-cell>
`})
class RowComp {
  @Input()  row!: Row;
  @Output() rowToggled = new EventEmitter<Row>();
  onToggle(v: boolean) {
    this.row.selected = v;
    this.rowToggled.emit(this.row);
  }
}

// 3. Table component — bubble again
@Component({ template: `
  <row-comp *ngFor="let r of rows"
            [row]="r"
            (rowToggled)="onRowToggled($event)">
  </row-comp>
`})
class TableComp {
  @Input()  rows!: Row[];
  @Output() selectionChanged = new EventEmitter<Row[]>();
  onRowToggled(r: Row) {
    this.selectionChanged.emit(
      this.rows.filter(x => x.selected));
  }
}

// 4. Page — finally subscribe
@Component({ template: `
  <table-comp [rows]="rows$ | async"
              (selectionChanged)="onSel($event)">
  </table-comp>
`})
class PageComp { /* … */ }
```

</div>

<div>

### Shared state + Observable plumbing

```typescript
// Service holding the source of truth
@Injectable({ providedIn: 'root' })
class SelectionService {
  private subj = new BehaviorSubject<Row[]>([]);
  readonly selected$ = this.subj.asObservable();

  setSelected(rows: Row[]) { this.subj.next(rows); }
  clear()                  { this.subj.next([]);   }
}

// Page glues it all together
@Component({ /* … */ })
class PageComp implements OnInit, OnDestroy {
  rows$!: Observable<Row[]>;
  // Signal for "are we busy?" — different system
  readonly busy = signal(false);
  private destroy$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private sel:  SelectionService,
    private fb:   FormBuilder) {
    // Effect — yet another reactive primitive
    effect(() => {
      const b = this.busy();
      document.body.classList.toggle('loading', b);
      if (b) console.debug('loading…');
    });
  }

  ngOnInit() {
    const filter$ = this.fb.control('').valueChanges
      .pipe(debounceTime(250),
            distinctUntilChanged(),
            startWith(''));

    this.rows$ = combineLatest([
      filter$,
      this.sel.selected$,
      this.http.get<Row[]>('/api/rows')
        .pipe(tap(() => this.busy.set(false)))
    ]).pipe(
      tap(() => this.busy.set(true)),
      map(([q, sel, all]) =>
        all.filter(r => r.name.includes(q))
           .map(r => ({...r,
              selected: sel.some(s => s.id === r.id)}))),
      takeUntil(this.destroy$));
  }

  onSel(rows: Row[]) { this.sel.setSelected(rows); }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

</div>

</div>

> Five classes. Four `@Output` chains. Three reactive
> primitives — Observable, Signal, Effect — wired together.
> One Subject for cleanup. **All to track which checkbox is ticked.**

---

# The same problem — in `semantic-ui`

Same table. Same checkboxes. Same "delete selected" action.
**One Spring controller:**

```java
@GetMapping("/products")
UiPage list() {
  var table = UiTable.of("products", "Products")
    .selectMode(SelectMode.MULTI)
    .column(UiColumn.text("name",  "Name").asSortable())
    .column(UiColumn.text("price", "Price"))
    .rowAction(UiAction.danger("delete", "Delete selected")
      .confirm("Delete all selected products?")
      .onClick(UiTrigger.api("POST", "/products/delete", "products")));

  repo.findAll().forEach(p -> table.row(Map.of(
    "id",    p.getId(),
    "name",  p.getName(),
    "price", money(p.getPrice()))));

  return UiPage.of("/products", table);
}

@PostMapping("/products/delete")
UiPage delete(@RequestParam Set<Long> ids) {
  repo.deleteAllById(ids);
  return list();
}
```

<br>

> **No `@Output` chains. No `BehaviorSubject`. No `Signal` or `Effect`.**
> The table knows what's selected. The server gets the IDs.
> **All to track which checkbox is ticked.**

---

<!-- _class: diagram -->

![bg fit](assets/how-it-works.svg)

---

<!-- _class: diagram -->

![bg fit](assets/chat-stream-patch.svg)

---

# Why semantic-ui

<div class="columns">

<div>

### 🧩 Separation of concerns

- **Developer** owns structure + behaviour
- **Designer** owns CSS + renderer
- No JSX, no template-in-component mixing
- **Sensible defaults** — usable without one line of CSS

### 🌱 Extensibility

- New node type = new class +
  Handlebars template + TS renderer
- Same pattern for diagrams, charts,
  markdown, custom widgets
- Backend agnostic — plain JSON;
  no backend needed at all, or bring
  any (Spring, Node, …)

</div>

<div>

### 🧠 Simple mental model

- One typed JSON vocabulary
- Server builds the tree or a patch
- Renderer paints it
- That's it. No hooks, no signals, no stores.

### ⚡ And along the way

- **Stateless backend**, no validation duplication
- **UiPatch** for diffs and realtime streams
- **SSR + SPA** from the same controller
- **WYSIWYG editor included**

</div>

</div>
