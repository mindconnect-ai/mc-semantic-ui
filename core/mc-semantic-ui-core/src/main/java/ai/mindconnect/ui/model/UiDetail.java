package ai.mindconnect.ui.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UiDetail extends UiNode {
    private List<UiField>  fields  = new ArrayList<>();
    private List<UiAction> actions = new ArrayList<>();
    private List<UiLink>   links   = new ArrayList<>();

    public UiDetail field(UiField field)    { fields.add(field);   return this; }
    public UiDetail action(UiAction action) { actions.add(action); return this; }
    public UiDetail link(UiLink link)       { links.add(link);     return this; }

    public static UiDetail of(String id, String title) {
        var d = new UiDetail();
        d.setId(id); d.setTitle(title);
        return d;
    }
}
