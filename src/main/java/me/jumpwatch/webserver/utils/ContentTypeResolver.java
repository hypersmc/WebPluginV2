package me.jumpwatch.webserver.utils;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static com.google.common.io.Files.getFileExtension;

/**
 * @author JumpWatch on 14-01-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class ContentTypeResolver {
    private Map<String, String> extensionToTypeMap;

    public ContentTypeResolver() {
        loadContentTypes();
    }

    private void loadContentTypes() {
        Yaml yaml = new Yaml();
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(main.getDataFolder() + "/mime_types.yml")) {
            Map<String, List<Map<String, String>>> yamlData = yaml.load(inputStream);
            List<Map<String, String>> extensions = yamlData.get("extensions");

            extensionToTypeMap = extensions.stream()
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    extension -> extension.get("extension"),
                                    extension -> extension.get("type")
                            )
                    );

        } catch (Exception e) {
            main.getLogger().info(e.toString());
        }
    }
    public void reloadContentTypes() {
        loadContentTypes();
    }
    public String getContentType(String fileRequested) {
        String extension = getFileExtension(fileRequested);

        return extensionToTypeMap.getOrDefault(extension, "text/plain");
    }
}
