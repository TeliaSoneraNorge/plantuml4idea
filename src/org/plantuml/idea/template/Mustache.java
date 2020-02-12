package org.plantuml.idea.template;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Mustache {

    static Pattern dataSourcePattern = Pattern.compile("'datasource=(.*)",Pattern.MULTILINE);

    public static String insertProperties(String text, JsonReader reader){
        Map<String, String> map = new HashMap<String, String>();
        Gson gson = new Gson();
        HashMap<String, Object> json = gson.fromJson(reader, HashMap.class);
        for(Map.Entry<String, Object> item: json.entrySet()){
            map.put(item.getKey(), item.getValue().toString());
        }
        return com.samskivert.mustache.Mustache.compiler().defaultValue("[KEY_NOT_FOUND]").compile(text).execute(map);
    }

    public static String applyPumlTemplating(String text, Project project){
        Matcher m = dataSourcePattern.matcher(text);
        if(m.find()){

            String templateDataFile = m.group(1);
            try {
                String pathToFile = project.getBasePath()+"/"+templateDataFile;
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(pathToFile);
                if(virtualFile == null){
                    return text;
                }
                PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
                if (file == null){
                    return text;
                }

                JsonReader reader = new JsonReader(new StringReader(file.getText()));
                return Mustache.insertProperties(text, reader);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        return text;
    }
}
