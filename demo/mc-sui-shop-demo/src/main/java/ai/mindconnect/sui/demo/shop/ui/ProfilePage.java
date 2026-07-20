package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.DemoUser;
import ai.mindconnect.ui.model.UiDetail;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiStack;

/**
 * Read-only profile view of the current user. Embedded as a body inside
 * the AdminPage shell — the same header + tab bar appear above. The
 * profile view does NOT light up either tab (Products / Customers); it's
 * a standalone destination the header avatar links to.
 */
public final class ProfilePage {

    private final DemoUser user;

    public ProfilePage(DemoUser user) {
        this.user = user;
    }

    /** Body that goes inside the AdminPage shell. */
    public UiStack renderBody() {
        var detail = UiDetail.of("profile-detail", "👤 Your profile")
                .field(UiField.text("name",     "Name",     user.name()))
                .field(UiField.text("initials", "Initials", user.initials()))
                .field(UiField.text("email",    "Email",    user.email()))
                .field(UiField.text("role",     "Role",     user.role()));

        // Wrap in a stack so the AdminPage's panel contract (UiNode child)
        // is the same regardless of which page is showing.
        return UiStack.of("profile-stack").child(detail);
    }
}
