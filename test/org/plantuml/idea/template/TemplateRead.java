package org.plantuml.idea.template;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.junit.Test;
import java.io.FileReader;
import java.util.HashMap;

public class TemplateRead {
    @Test
    public void readTest() throws Exception {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader("testData/template_properties.json"));
        HashMap<String, Object> json = gson.fromJson(reader, HashMap.class);
        System.out.println(json.get("field0").toString());
    }

}