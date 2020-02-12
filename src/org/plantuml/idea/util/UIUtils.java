package org.plantuml.idea.util;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.stream.JsonReader;
import com.intellij.json.psi.JsonFile;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ResourceUtil;
import com.samskivert.mustache.*;
import org.apache.commons.collections.map.AbstractMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.plantuml.idea.plantuml.PlantUml;
import org.plantuml.idea.rendering.LazyApplicationPoolExecutor;
import org.plantuml.idea.rendering.RenderCommand;
import org.plantuml.idea.toolwindow.PlantUmlToolWindow;
import org.plantuml.idea.toolwindow.PlantUmlToolWindowFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Eugene Steinberg
 */
public class UIUtils {
    private static Logger logger = Logger.getInstance(UIUtils.class);

    public static final NotificationGroup NOTIFICATION = new NotificationGroup("PlantUML integration plugin",
            NotificationDisplayType.BALLOON, true);


    public static BufferedImage getBufferedImage(@NotNull byte[] imageBytes) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(imageBytes);
        return ImageIO.read(input);
    }

    private static String applyTemplating(String text, Project project){
        Map<String, String> map = new HashMap<String, String>();
        Pattern p = Pattern.compile("'datasource=(.*)",Pattern.MULTILINE);
        Matcher m = p.matcher(text);
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

                Gson gson = new Gson();
                HashMap<String, Object> json = gson.fromJson(reader, HashMap.class);
                for(Map.Entry<String, Object> item: json.entrySet()){
                    map.put(item.getKey(), item.getValue().toString());
                }
                return Mustache.compiler().defaultValue("[KEY_NOT_FOUND]").compile(text).execute(map);
            } catch (JsonIOException | MustacheException e) {
                e.printStackTrace();
            }

        }

        return text;
    }


    public static String getSelectedSourceWithCaret(Project project) {
        String source = "";

        Editor selectedTextEditor = getSelectedTextEditor(FileEditorManager.getInstance(project));

        if (selectedTextEditor != null) {
            final Document document = selectedTextEditor.getDocument();
            int offset = selectedTextEditor.getCaretModel().getOffset();
            source = applyTemplating(document.getText(), project);
            source = PlantUml.extractSource(source, offset);
        }
        return source;
    }

    /**
     * FileEditorManager#getSelectedTextEditor is not good enough, returns null for *.rst in PyCharm (TextEditorWithPreview)
     */
    @Nullable
    public static Editor getSelectedTextEditor(FileEditorManager instance) {
        Editor selectedTextEditor = instance.getSelectedTextEditor();

        if (selectedTextEditor == null) {
            FileEditor selectedEditor = instance.getSelectedEditor();
            if (selectedEditor != null) {
                FileEditorLocation location = selectedEditor.getCurrentLocation();
                if (location instanceof TextEditorLocation) {
                    TextEditorLocation currentLocation = (TextEditorLocation) location;
                    FileEditor fileEditor = currentLocation.getEditor();
                    if (fileEditor instanceof TextEditor) {
                        TextEditor textEditor = (TextEditor) fileEditor;
                        selectedTextEditor = textEditor.getEditor();
                    }
                }
            }
        }
        return selectedTextEditor;
    }

    public static String getSelectedSource(FileEditorManager instance) {
        String source = "";
        Editor selectedTextEditor = getSelectedTextEditor(instance);
        if (selectedTextEditor != null) {
            final Document document = selectedTextEditor.getDocument();
            source = document.getText();
        }
        return source;
    }


    @Nullable
    public static VirtualFile getSelectedFile(FileEditorManager instance, FileDocumentManager fileDocumentManager) {
        Editor selectedTextEditor = getSelectedTextEditor(instance);
        VirtualFile file = null;
        if (selectedTextEditor != null) {
            final Document document = selectedTextEditor.getDocument();
            file = fileDocumentManager.getFile(document);
        }
        return file;
    }

    @Nullable
    public static File getSelectedDir(FileEditorManager instance, FileDocumentManager fileDocumentManager) {
        Editor selectedTextEditor = getSelectedTextEditor(instance);
        File baseDir = null;
        if (selectedTextEditor != null) {

            final Document document = selectedTextEditor.getDocument();
            final VirtualFile file = fileDocumentManager.getFile(document);
            baseDir = getParent(file);
        }
        return baseDir;
    }

    @Nullable
    public static File getParent(@Nullable VirtualFile file) {
        File baseDir = null;
        if (file != null) {
            VirtualFile parent = file.getParent();
            if (parent != null && parent.isDirectory()) {
                baseDir = new File(parent.getPath());
            }
        }
        return baseDir;
    }

    @Nullable
    public static PlantUmlToolWindow getPlantUmlToolWindow(@NotNull Project project) {
        PlantUmlToolWindow result = null;
        ToolWindow toolWindow = getToolWindow(project);
        if (toolWindow != null) {
            result = getPlantUmlToolWindow(toolWindow);
        }
        return result;
    }

    @Nullable
    public static PlantUmlToolWindow getPlantUmlToolWindow(@NotNull ToolWindow toolWindow) {
        PlantUmlToolWindow result = null;
        Content[] contents = toolWindow.getContentManager().getContents();
        if (contents.length > 0) {
            JComponent component = contents[0].getComponent();
            //it can be JLabel "Initializing..."
            if (component instanceof PlantUmlToolWindow) {
                result = (PlantUmlToolWindow) component;
            }
        }
        return result;
    }


    @Nullable
    public static ToolWindow getToolWindow(@NotNull Project project) {
        ToolWindowManager instance = ToolWindowManager.getInstance(project);
        if (instance == null) {
            return null;
        }
        return instance.getToolWindow(PlantUmlToolWindowFactory.ID);
    }

    public static void renderPlantUmlToolWindowLater(@Nullable Project project, LazyApplicationPoolExecutor.Delay delay, RenderCommand.Reason reason) {
        if (project == null) return;

        ToolWindow toolWindow = getToolWindow(project);
        if (toolWindow == null || !toolWindow.isVisible()) {
            return;
        }

        PlantUmlToolWindow plantUmlToolWindow = getPlantUmlToolWindow(toolWindow);
        if (plantUmlToolWindow != null) {
            plantUmlToolWindow.renderLater(delay, reason);
        }
    }


    public static boolean hasAnyImage(Project project) {
        PlantUmlToolWindow plantUmlToolWindow = getPlantUmlToolWindow(project);
        boolean hasAnyImage = false;
        if (plantUmlToolWindow != null) {
            hasAnyImage = plantUmlToolWindow.getNumPages() > 0;
        }
        return hasAnyImage;
    }
}
