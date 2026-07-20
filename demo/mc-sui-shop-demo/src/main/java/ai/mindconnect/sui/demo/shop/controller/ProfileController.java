package ai.mindconnect.sui.demo.shop.controller;

import ai.mindconnect.sui.demo.shop.DemoUser;
import ai.mindconnect.sui.demo.shop.SuiMode;
import ai.mindconnect.sui.demo.shop.SuiThemeRef;
import ai.mindconnect.sui.demo.shop.ui.AdminPage;
import ai.mindconnect.sui.demo.shop.ui.ProfilePage;
import ai.mindconnect.ui.model.UiPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-user profile page. Reached via the avatar widget in the admin
 * header. No tab is highlighted on this URL — Products and Customers are
 * both inactive in the tab bar, which is intentional (the profile lives
 * outside the primary navigation).
 */
@RestController
@RequestMapping("/admin/profile")
public class ProfileController {

    private final DemoUser user;
    private final SuiMode mode;
    private final SuiThemeRef theme;

    public ProfileController(DemoUser user, SuiMode mode, SuiThemeRef theme) {
        this.user = user;
        this.mode = mode;
        this.theme = theme;
    }

    @GetMapping
    public UiPage profile() {
        var body = new ProfilePage(user).renderBody();
        return new AdminPage(user, null, body, mode.isSpa(), theme.current()).render();
    }
}
