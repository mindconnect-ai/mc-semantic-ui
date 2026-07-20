package ai.mindconnect.sui.demo.shop.controller;

import ai.mindconnect.sui.demo.shop.CustomerService;
import ai.mindconnect.sui.demo.shop.DemoUser;
import ai.mindconnect.sui.demo.shop.SuiMode;
import ai.mindconnect.sui.demo.shop.SuiThemeRef;
import ai.mindconnect.sui.demo.shop.ui.AdminPage;
import ai.mindconnect.sui.demo.shop.ui.CustomerListPage;
import ai.mindconnect.ui.model.UiPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customers tab — list-only. Wraps the {@link CustomerListPage}'s body in
 * the shared {@link AdminPage} shell so the same tab bar shows on both
 * sides of the app.
 */
@RestController
@RequestMapping("/admin/customers")
public class CustomerController {

    private final CustomerService service;
    private final DemoUser user;
    private final SuiMode mode;
    private final SuiThemeRef theme;

    public CustomerController(CustomerService service, DemoUser user, SuiMode mode, SuiThemeRef theme) {
        this.service = service;
        this.user = user;
        this.mode = mode;
        this.theme = theme;
    }

    @GetMapping
    public UiPage list(
            @RequestParam(value = "q",    required = false) String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var customers = service.list(query, page, size);
        var body = new CustomerListPage(customers, query, page, size).renderBody();
        return new AdminPage(user, AdminPage.Tab.CUSTOMERS, body, mode.isSpa(), theme.current()).render();
    }
}
