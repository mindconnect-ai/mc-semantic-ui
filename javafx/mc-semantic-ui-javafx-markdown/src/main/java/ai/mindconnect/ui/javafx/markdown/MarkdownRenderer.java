package ai.mindconnect.ui.javafx.markdown;

import ai.mindconnect.ui.ext.markdown.UiMarkdown;
import ai.mindconnect.ui.javafx.FxNodeRenderer;
import ai.mindconnect.ui.javafx.FxRenderContext;
import ai.mindconnect.ui.javafx.SuiFxText;
import ai.mindconnect.ui.model.UiTrigger;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints {@code markdown} as a scene graph.
 *
 * <p>Not through a WebView. The obvious shortcut — render to HTML and hand it
 * to a browser engine — would drag a WebKit build onto every app that shows a
 * paragraph of text, and the result would sit in the window as a foreign
 * object: its own fonts, its own selection behaviour, its own scrollbars, deaf
 * to the {@code -sui-*} palette around it. Walking the AST into real controls
 * costs a parser and gives back text that belongs to the window it is in.
 *
 * <p>Links dispatch through the bus like any other, so a relative one in a
 * document resolves against the page it arrived on.
 */
public class MarkdownRenderer implements FxNodeRenderer<UiMarkdown> {

    // Tables are a GitHub extension rather than CommonMark. The browser gets
    // them for free -- marked has GFM on by default -- so without this the
    // desktop was the only one of the three renderers that showed a table as
    // the row of pipes it is written as.
    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    @Override
    public Node render(UiMarkdown node, FxRenderContext ctx) {
        var box = new VBox(8);
        box.getStyleClass().add("sui-markdown");
        if (!SuiFxText.present(node.getContent())) return box;

        var document = parser.parse(node.getContent());
        for (var child = document.getFirstChild(); child != null; child = child.getNext()) {
            var painted = block(child, ctx);
            if (painted != null) box.getChildren().add(painted);
        }
        return box;
    }

    /** One block-level node. {@code null} for anything with nothing to draw. */
    private Node block(org.commonmark.node.Node node, FxRenderContext ctx) {
        if (node instanceof Heading heading) {
            var label = new Label(textOf(heading));
            label.setWrapText(true);
            label.getStyleClass().addAll("sui-md-heading", "sui-md-h" + heading.getLevel());
            return label;
        }
        if (node instanceof Paragraph paragraph) {
            return flow(paragraph, ctx, "sui-md-paragraph");
        }
        if (node instanceof BulletList list) {
            return list(list, ctx, index -> "•  ");
        }
        if (node instanceof OrderedList list) {
            int start = list.getMarkerStartNumber() == null ? 1 : list.getMarkerStartNumber();
            return list(list, ctx, index -> (start + index) + ".  ");
        }
        if (node instanceof FencedCodeBlock code) {
            return codeBlock(code.getLiteral());
        }
        if (node instanceof IndentedCodeBlock code) {
            return codeBlock(code.getLiteral());
        }
        if (node instanceof BlockQuote quote) {
            var box = new VBox(6);
            box.getStyleClass().add("sui-md-quote");
            for (var child = quote.getFirstChild(); child != null; child = child.getNext()) {
                var painted = block(child, ctx);
                if (painted != null) box.getChildren().add(painted);
            }
            return box;
        }
        if (node instanceof TableBlock table) {
            return table(table, ctx);
        }
        if (node instanceof ThematicBreak) {
            var rule = new Separator();
            rule.getStyleClass().add("sui-md-rule");
            return rule;
        }
        // An unknown block still has text in it; showing that beats showing
        // nothing, which is the call the SPA's fallback makes too.
        var text = textOf(node);
        return SuiFxText.present(text) ? new Label(text) : null;
    }

    /**
     * A GFM table as a {@link GridPane}.
     *
     * <p>Not a {@code TableView}: that is a scrolling, sortable, virtualised
     * control for a data set, and this is a piece of prose. A grid sits in the
     * flow of the document at exactly the height its rows need, which is what
     * the {@code <table>} the other two renderers emit does.
     */
    private Node table(TableBlock table, FxRenderContext ctx) {
        var grid = new GridPane();
        grid.getStyleClass().add("sui-md-table");

        int row = 0;
        for (var section = table.getFirstChild(); section != null; section = section.getNext()) {
            boolean head = section instanceof TableHead;
            if (!head && !(section instanceof TableBody)) continue;

            for (var line = section.getFirstChild(); line != null; line = line.getNext()) {
                if (!(line instanceof TableRow)) continue;
                int column = 0;
                for (var cell = line.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (!(cell instanceof TableCell tableCell)) continue;
                    grid.add(cellOf(tableCell, ctx, head), column++, row);
                }
                row++;
            }
        }
        // Every column shares the width evenly and its text wraps, so a table
        // of prose does not run off the side of a window the way a fixed
        // layout would.
        int columns = grid.getChildren().stream()
                .mapToInt(n -> GridPane.getColumnIndex(n) == null ? 0 : GridPane.getColumnIndex(n))
                .max().orElse(0) + 1;
        for (int i = 0; i < columns; i++) {
            var constraint = new ColumnConstraints();
            constraint.setHgrow(Priority.SOMETIMES);
            constraint.setPercentWidth(100.0 / columns);
            grid.getColumnConstraints().add(constraint);
        }
        return grid;
    }

    private Node cellOf(TableCell cell, FxRenderContext ctx, boolean head) {
        var flow = flow(cell, ctx, head ? "sui-md-th" : "sui-md-td");
        if (cell.getAlignment() == TableCell.Alignment.RIGHT) {
            flow.setTextAlignment(javafx.scene.text.TextAlignment.RIGHT);
        } else if (cell.getAlignment() == TableCell.Alignment.CENTER) {
            flow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        }
        if (head) {
            // A header cell is bold whatever the markdown says, the way a
            // <th> is -- the alignment column of a GFM table carries no
            // emphasis of its own.
            flow.getChildren().forEach(n -> n.getStyleClass().add("sui-md-strong"));
        }
        return flow;
    }

    private Node codeBlock(String literal) {
        var label = new Label(literal == null ? "" : literal.stripTrailing());
        label.getStyleClass().add("sui-md-code-block");
        // Code does not reflow: wrapping it would break the alignment that is
        // half of what makes it readable. It scrolls with the page instead.
        label.setWrapText(false);
        return label;
    }

    private Node list(org.commonmark.node.Node list, FxRenderContext ctx,
                      java.util.function.IntFunction<String> marker) {
        var box = new VBox(4);
        box.getStyleClass().add("sui-md-list");

        int index = 0;
        for (var item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) continue;

            var bullet = new Label(marker.apply(index++));
            bullet.getStyleClass().add("sui-md-bullet");
            bullet.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

            var body = new VBox(4);
            HBox.setHgrow(body, Priority.ALWAYS);
            for (var child = item.getFirstChild(); child != null; child = child.getNext()) {
                var painted = block(child, ctx);
                if (painted != null) body.getChildren().add(painted);
            }

            var row = new HBox(4, bullet, body);
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }
        return box;
    }

    /** A block's inline content as one {@link TextFlow}. */
    private TextFlow flow(org.commonmark.node.Node block, FxRenderContext ctx, String styleClass) {
        var flow = new TextFlow();
        flow.getStyleClass().add(styleClass);
        for (var child = block.getFirstChild(); child != null; child = child.getNext()) {
            inline(child, ctx, List.of(), flow.getChildren());
        }
        return flow;
    }

    /**
     * Appends one inline node's painted form.
     *
     * <p>{@code styles} accumulates down the tree, so bold inside italic
     * arrives carrying both — the styling is CSS classes rather than fonts
     * built here, which is what lets a host restyle markdown like everything
     * else.
     */
    private void inline(org.commonmark.node.Node node, FxRenderContext ctx,
                        List<String> styles, List<Node> out) {

        if (node instanceof org.commonmark.node.Text text) {
            out.add(styled(new Text(text.getLiteral()), styles));
            return;
        }
        if (node instanceof StrongEmphasis) {
            children(node, ctx, plus(styles, "sui-md-strong"), out);
            return;
        }
        if (node instanceof Emphasis) {
            children(node, ctx, plus(styles, "sui-md-emphasis"), out);
            return;
        }
        if (node instanceof Code code) {
            out.add(styled(new Text(code.getLiteral()), plus(styles, "sui-md-code")));
            return;
        }
        if (node instanceof SoftLineBreak) {
            // A single newline in markdown is a space, not a break.
            out.add(styled(new Text(" "), styles));
            return;
        }
        if (node instanceof HardLineBreak) {
            out.add(styled(new Text("\n"), styles));
            return;
        }
        if (node instanceof Link link) {
            var hyperlink = new Hyperlink(textOf(link));
            hyperlink.getStyleClass().add("sui-md-link");
            var href = link.getDestination();
            if (SuiFxText.present(href)) {
                // Through the bus, so a relative href resolves against the page
                // the document came on — same as a link anywhere else.
                hyperlink.setOnAction(e -> ctx.bus().dispatch(UiTrigger.go(href), null, ctx));
            }
            out.add(hyperlink);
            return;
        }
        if (node instanceof Image image) {
            // No image loading here: a document's images are the one thing that
            // can hang a window on a slow link. The alt text says what is
            // missing, which is more use than a blank.
            var alt = textOf(image);
            out.add(styled(new Text(SuiFxText.present(alt) ? "[" + alt + "]" : "[image]"),
                    plus(styles, "sui-md-image-alt")));
            return;
        }
        children(node, ctx, styles, out);
    }

    private void children(org.commonmark.node.Node node, FxRenderContext ctx,
                          List<String> styles, List<Node> out) {
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            inline(child, ctx, styles, out);
        }
    }

    private static Text styled(Text text, List<String> styles) {
        text.getStyleClass().add("sui-md-text");
        text.getStyleClass().addAll(styles);
        return text;
    }

    private static List<String> plus(List<String> styles, String extra) {
        var next = new ArrayList<>(styles);
        next.add(extra);
        return next;
    }

    /** All the text under a node, which is what a heading or a link label is. */
    private static String textOf(org.commonmark.node.Node node) {
        var sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(org.commonmark.node.Node node, StringBuilder sb) {
        for (var child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text text) sb.append(text.getLiteral());
            else if (child instanceof Code code) sb.append(code.getLiteral());
            else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) sb.append(' ');
            else collect(child, sb);
        }
    }
}
