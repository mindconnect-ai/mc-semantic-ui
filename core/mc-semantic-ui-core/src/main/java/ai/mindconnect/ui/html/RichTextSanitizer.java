package ai.mindconnect.ui.html;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reduces HTML to what a RICHTEXT field's toolbar produces: paragraphs, line
 * breaks, bold, italic, underline, lists, quotes, headings, code, links to
 * http(s), mail or phone, and images embedded as data or fetched over
 * http(s). Script, style and embedded documents go with their content, other
 * tags are unwrapped (their text stays), and no attribute survives but a
 * checked {@code href}, {@code src}, {@code alt}, {@code width} or
 * {@code height}.
 *
 * <p>The field's templates run every value through this before rendering it,
 * and the browser renderer does the same with {@code sanitizeRichText()} —
 * the two are one policy, held together by
 * {@code src/test/resources/richtext/sanitize-cases.json}, which both test
 * suites run. Call it yourself on what a form submits before you store it:
 * a value is only as clean as the last place that cleaned it.
 *
 * <p>Safe by construction: the output is rebuilt from scratch — allowed tags
 * with checked attributes, written here, and every other character of the
 * input as escaped text. Nothing of the input reaches a tag or an attribute
 * position unexamined. An attribute value is checked after its character
 * references are resolved, and written as that resolved value escaped afresh,
 * so what a browser reads is exactly what was checked.
 */
public final class RichTextSanitizer {

    private RichTextSanitizer() {}

    private static final Set<String> ALLOWED = Set.of(
            "p", "br", "div", "span",
            "b", "strong", "i", "em", "u", "s", "strike", "sub", "sup", "code", "pre",
            "ul", "ol", "li", "blockquote", "a", "hr", "img",
            "h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> VOID = Set.of("br", "hr", "img");

    private static final String WS = "[ \\t\\n\\f\\r]";
    private static final Pattern COMMENT = Pattern.compile("<!--[\\s\\S]*?(?:-->|(?![\\s\\S]))");
    private static final Pattern DECLARATION = Pattern.compile("<[!?][\\s\\S]*?>");
    private static final Pattern RAW_CONTENT = Pattern.compile(
            "<(script|style|head|title|template|noscript|iframe|object|embed|applet|svg|math|form|button|textarea|select|option|xmp|noembed|noframes|plaintext)"
                    + "(?![a-zA-Z0-9:-])[\\s\\S]*?(?:</\\1" + WS + "*>|(?![\\s\\S]))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9:-]*)((?:" + WS + "+[^ \\t\\n\\f\\r\"'>/=]+(?:" + WS + "*=" + WS
                    + "*(?:\"[^\"]*\"|'[^']*'|[^ \\t\\n\\f\\r\"'>]+))?)*)" + WS + "*/?>");
    private static final Pattern ATTR = Pattern.compile(
            "([^ \\t\\n\\f\\r\"'>/=]+)(?:" + WS + "*=" + WS + "*(?:\"([^\"]*)\"|'([^']*)'|([^ \\t\\n\\f\\r\"'>]+)))?");
    private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{0,31});?");
    private static final Pattern STRAY_AMP = Pattern.compile("&(?!(?:#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{0,31});)");
    private static final Pattern CONTROLS = Pattern.compile("[\\x{0000}-\\x{0020}\\x{007f}-\\x{009f}]");
    private static final Pattern IMAGE_DATA = Pattern.compile("^data:image/(png|jpeg|jpg|gif|webp|bmp);base64,");

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
            Map.entry("apos", "'"), Map.entry("nbsp", "\u00a0"), Map.entry("colon", ":"), Map.entry("period", "."),
            Map.entry("plus", "+"), Map.entry("sol", "/"), Map.entry("num", "#"), Map.entry("excl", "!"),
            Map.entry("quest", "?"), Map.entry("equals", "="), Map.entry("lpar", "("), Map.entry("rpar", ")"),
            Map.entry("Tab", "\t"), Map.entry("NewLine", "\n"));

    /** The sanitised HTML; empty for null or empty input. */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) return "";
        String input = RAW_CONTENT.matcher(
                DECLARATION.matcher(COMMENT.matcher(html).replaceAll("")).replaceAll("")).replaceAll("");
        var out = new StringBuilder(input.length());
        Deque<Boolean> anchors = new ArrayDeque<>();
        int last = 0;
        Matcher m = TAG.matcher(input);
        while (m.find()) {
            out.append(escapeText(input.substring(last, m.start())));
            last = m.end();
            boolean closing = "/".equals(m.group(1));
            String tag = m.group(2).toLowerCase(java.util.Locale.ROOT);
            if (!ALLOWED.contains(tag)) continue;
            if (closing) {
                if (VOID.contains(tag)) continue;
                if (tag.equals("a") && !Boolean.TRUE.equals(anchors.pollLast())) continue;
                out.append("</").append(tag).append('>');
                continue;
            }
            String attrs = keptAttributes(tag, m.group(3) == null ? "" : m.group(3));
            if (tag.equals("a")) anchors.addLast(attrs != null);
            if (attrs == null) continue;
            out.append('<').append(tag).append(attrs).append('>');
        }
        return out.append(escapeText(input.substring(last))).toString();
    }

    /** Whether a link target may be kept: http(s), mailto, tel, or relative — never a scheme that runs. */
    public static boolean safeHref(String href) {
        String v = schemeView(href);
        if (v.isEmpty()) return false;
        if (v.startsWith("http:") || v.startsWith("https:") || v.startsWith("mailto:") || v.startsWith("tel:")) return true;
        return !v.matches("^[a-z][a-z0-9+.-]*:[\\s\\S]*");
    }

    /** Whether an image source may be kept: raster image data, or http(s). */
    public static boolean safeImageSrc(String src) {
        String v = schemeView(src);
        return IMAGE_DATA.matcher(v).find() || v.startsWith("http://") || v.startsWith("https://");
    }

    /** An attribute value with its character references resolved. */
    public static String decodeEntities(String value) {
        return ENTITY.matcher(value).replaceAll(r -> {
            String ent = r.group(1);
            String decoded;
            if (ent.charAt(0) == '#') {
                long cp = (ent.charAt(1) == 'x' || ent.charAt(1) == 'X')
                        ? Long.parseLong(ent.substring(2), 16) : Long.parseLong(ent.substring(1));
                decoded = cp > 0 && cp <= 0x10ffff && !(cp >= 0xd800 && cp <= 0xdfff)
                        ? new String(Character.toChars((int) cp)) : "�";
            } else {
                decoded = NAMED.getOrDefault(ent, r.group());
            }
            return Matcher.quoteReplacement(decoded);
        });
    }

    private static String schemeView(String url) {
        return url == null ? "" : CONTROLS.matcher(url).replaceAll("").toLowerCase(java.util.Locale.ROOT);
    }

    private static String keptAttributes(String tag, String attrText) {
        var out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        boolean hasHref = false, hasSrc = false;
        Matcher m = ATTR.matcher(attrText);
        while (m.find()) {
            String name = m.group(1).toLowerCase(java.util.Locale.ROOT);
            if (!seen.add(name)) continue;       // a browser keeps the first; so does this
            String raw = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : "";
            String value = decodeEntities(raw);
            boolean keep = (tag.equals("a") && name.equals("href") && safeHref(value))
                    || (tag.equals("img") && name.equals("src") && safeImageSrc(value))
                    || (tag.equals("img") && name.equals("alt"))
                    || (tag.equals("img") && (name.equals("width") || name.equals("height")) && value.matches("[0-9]{1,5}"));
            if (!keep) continue;
            if (name.equals("href")) hasHref = true;
            if (name.equals("src")) hasSrc = true;
            out.append(' ').append(name).append("=\"").append(escapeAttr(value)).append('"');
        }
        if ((tag.equals("a") && !hasHref) || (tag.equals("img") && !hasSrc)) return null;
        return out.toString();
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeText(String text) {
        return STRAY_AMP.matcher(text).replaceAll("&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
