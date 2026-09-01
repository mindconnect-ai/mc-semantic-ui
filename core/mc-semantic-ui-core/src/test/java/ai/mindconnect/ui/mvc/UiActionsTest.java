package ai.mindconnect.ui.mvc;

import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static ai.mindconnect.ui.mvc.UiActions.ROW_ID;
import static ai.mindconnect.ui.mvc.UiActions.streaming;
import static ai.mindconnect.ui.mvc.UiActions.trigger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;

/** A trigger derived from the handler it calls, rather than from a string. */
class UiActionsTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @RestController
    @RequestMapping("/api/things")
    static class ThingController {

        @GetMapping("/{id}")
        public UiPatch view(@PathVariable UUID id) { return new UiPatch(); }

        @DeleteMapping("/{id}")
        public UiPatch remove(@PathVariable UUID id) { return new UiPatch(); }

        @PostMapping("/{id}/notes")
        public UiPatch addNote(@PathVariable UUID id, @RequestParam String text) { return new UiPatch(); }

        @PostMapping("/{id}/run")
        public UiPatch run(@PathVariable UUID id) { return new UiPatch(); }
    }

    @Test
    void thePathComesFromTheMapping() {
        UiTrigger t = trigger(on(ThingController.class).view(ID));

        assertEquals("/api/things/" + ID, t.getUrl());
    }

    @Test
    void theVerbComesFromTheMappingToo() {
        assertEquals("DELETE", trigger(on(ThingController.class).remove(ID)).getMethod());
        assertEquals("POST", trigger(on(ThingController.class).run(ID)).getMethod());
    }

    @Test
    void requestParametersBecomeTheQuery() {
        UiTrigger t = trigger(on(ThingController.class).addNote(ID, "hello"));

        assertEquals("/api/things/" + ID + "/notes?text=hello", t.getUrl());
    }

    /** Without this a value with a space would land raw in the URL. */
    @Test
    void valuesAreEncoded() {
        UiTrigger t = trigger(on(ThingController.class).addNote(ID, "two words//"));

        assertEquals("/api/things/" + ID + "/notes?text=two%20words//", t.getUrl());
    }

    @Test
    void theRowSentinelRendersAsThePlaceholder() {
        UiTrigger t = trigger(on(ThingController.class).view(ROW_ID));

        assertEquals("/api/things/{id}", t.getUrl());
        assertFalse(t.getUrl().contains("%7B"), "braces must not be percent-encoded");
    }

    @Test
    void theStreamingVariantKeepsItsBehaviour() {
        UiTrigger plain  = trigger(on(ThingController.class).run(ID));
        UiTrigger stream = streaming(on(ThingController.class).run(ID), "form-1");

        assertEquals(plain.getUrl(), stream.getUrl());
        assertEquals(UiTrigger.Behavior.STREAM, stream.getBehavior());
        assertEquals("form-1", stream.getPayload());
    }
}
