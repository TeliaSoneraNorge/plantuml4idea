package org.plantuml.idea.template;

import com.google.gson.stream.JsonReader;
import org.junit.Test;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;

public class TemplateRead {
    @Test
    public void testTemplateSubstitute() throws Exception {
        JsonReader reader = new JsonReader(new StringReader("{\"propName\":\"123\"}"));
        String text = Mustache.insertProperties("-{{       propName      }}-", reader);
        assertEquals(text, "-123-");
    }

}