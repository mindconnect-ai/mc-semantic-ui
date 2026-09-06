package ai.mindconnect.sui.demo.stateful;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import ai.mindconnect.ui.stateful.RouteParams;
import ai.mindconnect.ui.stateful.SuiRoute;
import ai.mindconnect.ui.stateful.SuiView;

/** The smallest possible stateful view. Open two tabs: each has its own count. */
@SuiRoute("/counter")
public class CounterView extends SuiView<CounterView.State> {

    public static class State {
        int count;
    }

    @Override
    protected State initialState(RouteParams params) {
        State s = new State();
        s.count = params.queryInt("start", 0);
        return s;
    }

    @Override
    protected UiNode render(State s) {
        title("Counter · stateful demo");
        return UiStack.of("counter")
                .child(UiText.of("count", "Clicked " + s.count + " time" + (s.count == 1 ? "" : "s")))
                .child(UiStack.of("buttons").direction(UiStack.Direction.HORIZONTAL)
                        .child(on(UiAction.primary("inc", "+1")).click(e -> s.count++))
                        .child(on(UiAction.secondary("dec", "−1")).click(e -> s.count--))
                        .child(on(UiAction.danger("reset", "Reset").confirm("Back to zero?")).click(e -> {
                            s.count = 0;
                            toast("Reset.");
                        })))
                .child(UiText.of("hint", "Reload the page: the count survives. Open the same address in a "
                        + "second tab: it gets its own instance."))
                .child(UiLink.of("back", "/products", "← Products"));
    }
}
