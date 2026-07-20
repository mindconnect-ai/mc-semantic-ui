package ai.mindconnect.sui.demo.shop.ui;

import ai.mindconnect.sui.demo.shop.jpa.Customer;
import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiTrigger;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * Customer list — mirrors {@link ProductListPage} in shape but read-only
 * (no create/edit/delete). Used as the content of the "Customers" tab on
 * the {@link AdminPage}.
 *
 * <p>Returns a {@link UiSection} (not a full {@link ai.mindconnect.ui.model.UiPage})
 * because it's embedded inside the admin page's tab structure; the
 * outer page wrapper is the AdminPage's responsibility.
 */
public final class CustomerListPage {

    private final Page<Customer> page;
    private final String query;
    private final int pageNumber;
    private final int pageSize;

    public CustomerListPage(Page<Customer> page, String query, int pageNumber, int pageSize) {
        this.page = page;
        this.query = query == null ? "" : query;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    /** Body that goes inside the AdminPage's "Customers" tab panel. */
    public UiStack renderBody() {
        var searchForm = buildSearchForm();
        var table      = buildTable();
        // Vertical stack: search bar on top, table below.
        return UiStack.of("customer-page-stack")
                .child(searchForm)
                .child(table);
    }

    private UiForm buildSearchForm() {
        // GET form so the query lands in the URL (shareable / bookmarkable),
        // exactly like the product search.
        return UiForm.of("customer-search", null)
                .field(UiField.text("q", "Search", query)
                        .asEditable()
                        .placeholder("name, email or customer number")
                        .hint("Empty matches everything."))
                .action(UiAction.primary("search", "Search")
                        .dispatch("GET", "/admin/customers"));
    }

    private UiTable buildTable() {
        var table = UiTable.of("customer-table", null)
                .column(UiTable.Column.text("customerNumber", "Customer #").asSortable())
                .column(UiTable.Column.text("name",           "Name").asSortable())
                .column(UiTable.Column.text("email",          "Email"))
                .column(UiTable.Column.text("city",           "City"));

        for (Customer c : page.getContent()) {
            table.row(Map.of(
                    "id",             c.getId().toString(),
                    "customerNumber", c.getCustomerNumber(),
                    "name",           c.getName(),
                    "email",          c.getEmail(),
                    "city",           c.getCity() == null ? "" : c.getCity()
            ));
        }

        UiTrigger pageTrigger = UiTrigger.go(
                "/admin/customers?q=" + urlEncode(query) + "&page={page}");
        table.paginate(pageNumber, pageSize, page.getTotalElements(), pageTrigger);

        return table;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
