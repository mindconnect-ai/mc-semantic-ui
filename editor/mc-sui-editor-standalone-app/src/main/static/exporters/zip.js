/*
 * Minimal, dependency-free ZIP writer (STORE method — no compression).
 *
 * The whole point of this app is to be self-contained with no external
 * scripts, so we can't pull in JSZip from a CDN. A stored-entry ZIP is simple
 * enough to emit by hand: per-file local headers, a central directory, and an
 * end-of-central-directory record. STORE (method 0) means compressed size ==
 * uncompressed size, which keeps the format trivial and is fine here — the
 * payload is source text and small JSON, and the user unzips it once.
 */

const CRC_TABLE = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
        t[n] = c >>> 0;
    }
    return t;
})();

function crc32(bytes) {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
}

const enc = new TextEncoder();
const toBytes = (data) => (typeof data === "string" ? enc.encode(data)
    : data instanceof Uint8Array ? data : new Uint8Array(data));

/**
 * Builds a ZIP Blob from a list of files.
 * @param {Array<{name: string, data: string|Uint8Array|ArrayBuffer}>} files
 * @returns {Blob}
 */
export function zipSync(files) {
    const entries = files.map(f => {
        const nameBytes = enc.encode(f.name);
        const dataBytes = toBytes(f.data);
        return { nameBytes, dataBytes, crc: crc32(dataBytes), size: dataBytes.length };
    });

    const chunks = [];
    const central = [];
    let offset = 0;

    for (const e of entries) {
        const local = new Uint8Array(30 + e.nameBytes.length);
        const dv = new DataView(local.buffer);
        dv.setUint32(0, 0x04034b50, true);   // local file header signature
        dv.setUint16(4, 20, true);           // version needed
        dv.setUint16(6, 0, true);            // flags
        dv.setUint16(8, 0, true);            // method: 0 = store
        dv.setUint16(10, 0, true);           // mod time
        dv.setUint16(12, 0x21, true);        // mod date (1980-01-01)
        dv.setUint32(14, e.crc, true);       // crc-32
        dv.setUint32(18, e.size, true);      // compressed size
        dv.setUint32(22, e.size, true);      // uncompressed size
        dv.setUint16(26, e.nameBytes.length, true);
        dv.setUint16(28, 0, true);           // extra length
        local.set(e.nameBytes, 30);
        chunks.push(local, e.dataBytes);

        const cen = new Uint8Array(46 + e.nameBytes.length);
        const cdv = new DataView(cen.buffer);
        cdv.setUint32(0, 0x02014b50, true);  // central dir signature
        cdv.setUint16(4, 20, true);          // version made by
        cdv.setUint16(6, 20, true);          // version needed
        cdv.setUint16(8, 0, true);
        cdv.setUint16(10, 0, true);          // method: store
        cdv.setUint16(12, 0, true);
        cdv.setUint16(14, 0x21, true);
        cdv.setUint32(16, e.crc, true);
        cdv.setUint32(20, e.size, true);
        cdv.setUint32(24, e.size, true);
        cdv.setUint16(28, e.nameBytes.length, true);
        cdv.setUint16(30, 0, true);          // extra
        cdv.setUint16(32, 0, true);          // comment
        cdv.setUint16(34, 0, true);          // disk number
        cdv.setUint16(36, 0, true);          // internal attrs
        cdv.setUint32(38, 0, true);          // external attrs
        cdv.setUint32(42, offset, true);     // local header offset
        cen.set(e.nameBytes, 46);
        central.push(cen);

        offset += local.length + e.dataBytes.length;
    }

    const centralSize = central.reduce((n, c) => n + c.length, 0);
    const end = new Uint8Array(22);
    const edv = new DataView(end.buffer);
    edv.setUint32(0, 0x06054b50, true);      // end of central dir signature
    edv.setUint16(8, entries.length, true);  // entries on this disk
    edv.setUint16(10, entries.length, true); // total entries
    edv.setUint32(12, centralSize, true);    // central dir size
    edv.setUint32(16, offset, true);         // central dir offset
    edv.setUint16(20, 0, true);              // comment length

    return new Blob([...chunks, ...central, end], { type: "application/zip" });
}

/** Prompts the browser to download {@code blob} as {@code filename}. */
export function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
