/*
 * Type aliases for the wire payloads the editor exchanges with the server.
 *
 * Kept loose on purpose: UiNode is `any` because the editor treats nodes as
 * opaque JSON values. The only thing it inspects directly is the `type`
 * discriminator. Specific node-shape knowledge lives in the schema returned
 * by /editor/api/schema (the NodeRegistry on the server side).
 */

// The JSON discriminator value, e.g. "form", "field", "action".
export type NodeType = string;

// A UiNode-shaped JSON object. We intentionally use `any` for child-list
// values because they can be UiNode[], UiField.Option[], UiAction[] etc.
// The editor stays generic; the schema disambiguates per property.
export type UiNodeJson = {
    type: NodeType;
    [key: string]: any;
};

export interface EditorContent {
    root?: UiNodeJson | null;
}

// ── Schema mirror of NodeRegistry on the Java side ──────────────────────────

export type PropertyKind =
    | "STRING" | "NUMBER" | "BOOLEAN" | "ENUM"
    | "STRING_LIST" | "NODE_LIST" | "OBJECT";

export interface PropertyMeta {
    name: string;
    kind: PropertyKind;
    required: boolean;
    enumValues?: string[] | null;
}

export type Cardinality = "LIST" | "SINGLE";

export interface ChildrenMeta {
    /** Name of the property that holds the child(ren) (e.g. "fields", "node"). */
    property: string;
    /** LIST = array of UiNode (default), SINGLE = a single UiNode slot. */
    cardinality: Cardinality;
    /** Type discriminators allowed in this slot. */
    allowedTypes: NodeType[];
}

export interface NodeMeta {
    type: NodeType;
    label: string;
    category: string;
    properties: PropertyMeta[];
    children: ChildrenMeta[];
}

/** Indexable schema map keyed by node type. */
export type Schema = Record<NodeType, NodeMeta>;
