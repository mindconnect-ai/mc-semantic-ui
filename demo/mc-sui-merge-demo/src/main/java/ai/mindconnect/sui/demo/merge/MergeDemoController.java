package ai.mindconnect.sui.demo.merge;

import ai.mindconnect.sui.demo.merge.ui.MergeDemoPage;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Every endpoint here does the same three things: change one bit of state,
 * send the smallest patch that says so, and write down what it sent.
 *
 * <p>The writing-down is the demo. A response carries two operations — the one
 * being demonstrated, and a {@code REPLACE} of the log that reports it — and
 * the byte counts are of the first alone, or the log would be measuring itself.
 */
@RestController
@RequiredArgsConstructor
public class MergeDemoController {

    private final DemoState state;
    private final ObjectMapper mapper;

    @GetMapping(path = "/", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public UiPage index() {
        return new MergeDemoPage(state).render();
    }

    /** Back to a clean page, the long way round: a whole new tree. */
    @PostMapping(path = "/api/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiPage reset() {
        state.reset();
        return new MergeDemoPage(state).render();
    }

    /**
     * Hide or show the advanced panel.
     *
     * <p>Three lines of server code for the whole feature, which is the point:
     * {@code hide} and {@code show} are merges of the one attribute, and the
     * panel's four fields are never mentioned, never serialised, never sent.
     */
    @PostMapping(path = "/api/advanced/{how}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiPatch advanced(@PathVariable String how) {
        var operation = switch (how) {
            case "hidden" -> UiPatch.Operation.hide(MergeDemoPage.ADVANCED);
            case "blank" -> UiPatch.Operation.hide(MergeDemoPage.ADVANCED, UiNode.Display.BLANK);
            default -> UiPatch.Operation.show(MergeDemoPage.ADVANCED);
        };
        state.setAdvanced(switch (how) {
            case "hidden" -> UiNode.Display.HIDDEN;
            case "blank" -> UiNode.Display.BLANK;
            default -> null;
        });

        var page = new MergeDemoPage(state);
        return respond(page,
                switch (how) {
                    case "hidden" -> "hide(\"advanced-card\")";
                    case "blank" -> "hide(\"advanced-card\", BLANK)";
                    default -> "show(\"advanced-card\")";
                },
                UiPatch.Operation.replace(MergeDemoPage.ADVANCED, page.advancedCard()),
                operation);
    }

    /**
     * Flip the notification setting.
     *
     * <p>Two nodes change and each is merged on its own. The button keeps its
     * icon, its trigger and its id without any of them being resent — and the
     * {@code REPLACE} shown beside it is close in size here on purpose. The
     * operation is not a compression trick; it is about not having to know, or
     * rebuild, the parts you are not changing.
     */
    @PostMapping(path = "/api/notifications/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public UiPatch notifications() {
        state.toggleNotifications();
        var page = new MergeDemoPage(state);
        boolean on = state.notifications();

        return respond(page,
                "merge(\"notify-toggle\", label + style + icon)",
                UiPatch.Operation.replace(MergeDemoPage.NOTIFY, page.notifyButton()),
                UiPatch.Operation.merge(MergeDemoPage.NOTIFY, Map.of(
                        "label", on ? "Notifications are on" : "Notifications are off",
                        "style", on ? "PRIMARY" : "SECONDARY",
                        "icon", on ? "bell" : "bell-off")),
                // The status line is a second merge rather than a second node:
                // the same argument, one level down.
                UiPatch.Operation.merge(MergeDemoPage.NOTIFY_STATUS, Map.of(
                        "text", page.notifyStatus().getText(),
                        "cssClass", page.notifyStatus().getCssClass())));
    }

    /**
     * The patch to send: the operations being demonstrated, and then the log
     * entry that says what they were.
     *
     * <p>The log goes last because a patch means what it means in order, and
     * a reader of the network tab should see the demonstration before the
     * bookkeeping. Only the first operation is measured — the rest, where
     * there are any, are the same argument repeated on a second node.
     */
    private UiPatch respond(MergeDemoPage page, String what,
                            UiPatch.Operation instead, UiPatch.Operation... operations) {
        state.record(new DemoState.Exchange(
                what, pretty(operations[0]), wireBytes(operations[0]),
                pretty(instead), wireBytes(instead)));

        var patch = UiPatch.of();
        for (var operation : operations) patch.patch(operation);
        return patch.patch(UiPatch.Operation.replace(MergeDemoPage.WIRE, page.wireLog()));
    }

    /** One operation, laid out so it can be read on the page. */
    private String pretty(UiPatch.Operation operation) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(operation);
        } catch (Exception e) {
            return "(could not be serialised: " + e.getMessage() + ")";
        }
    }

    /** What the same operation weighs sent the way a server actually sends it. */
    private int wireBytes(UiPatch.Operation operation) {
        try {
            return mapper.writeValueAsBytes(operation).length;
        } catch (Exception e) {
            return 0;
        }
    }
}
