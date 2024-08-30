package me.jumpwatch.webserver.utils;

import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.WebCoreProxy;
import me.jumpwatch.webserver.WebCoreProxyVel;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.io.Files.getFileExtension;

/**
 * @author JumpWatch on 14-01-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class ContentTypeResolver {
    private Logger logger = Logger.getLogger("WebPluginContentTypeResolver");
    public static Map<String, String> extensionToTypeMap;

    public ContentTypeResolver() {}

    public void loadContentTypesBukkit() {
        Yaml yaml = new Yaml();
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        try (InputStream inputStream = main.getDataFolder().toPath().resolve("mime_types.yml").toUri().toURL().openStream()) {
            Map<String, List<Map<String, String>>> yamlData = yaml.load(inputStream);
            if (yamlData != null) {
                List<Map<String, String>> extensions = yamlData.get("extensions");
                if (extensions != null) {
                    extensionToTypeMap = extensions.stream()
                            .collect(
                                    Collectors.toMap(
                                            extension -> extension.get("extension"),
                                            extension -> extension.get("type")
                                    )
                            );
                } else {
                    logger.info("No 'extensions' key found in the YAML file.");
                }
            } else {
                logger.info("Failed to load YAML data.");
            }
        } catch (IOException e) {
            logger.info("Error loading YAML file: " + e.getMessage());
        }
    }
    public void loadContentTypesVel() {
        Yaml yaml = new Yaml();

        try (InputStream inputStream = WebCoreProxyVel.dataFolder.toPath().resolve("mime_types.yml").toUri().toURL().openStream()) {
            Map<String, List<Map<String, String>>> yamlData = yaml.load(inputStream);
            if (yamlData != null) {
                List<Map<String, String>> extensions = yamlData.get("extensions");
                if (extensions != null) {
                    extensionToTypeMap = extensions.stream()
                            .collect(
                                    Collectors.toMap(
                                            extension -> extension.get("extension"),
                                            extension -> extension.get("type")
                                    )
                            );
                } else {
                    logger.info("No 'extensions' key found in the YAML file.");
                }
            } else {
                logger.info("Failed to load YAML data.");
            }
        } catch (IOException e) {
            logger.info("Error loading YAML file: " + e.getMessage());
        }
    }
    public void loadContentTypesBun() {
        Yaml yaml = new Yaml();

        try (InputStream inputStream = WebCoreProxy.dataFolder.toPath().resolve("mime_types.yml").toUri().toURL().openStream()) {
            Map<String, List<Map<String, String>>> yamlData = yaml.load(inputStream);
            if (yamlData != null) {
                List<Map<String, String>> extensions = yamlData.get("extensions");
                if (extensions != null) {
                    extensionToTypeMap = extensions.stream()
                            .collect(
                                    Collectors.toMap(
                                            extension -> extension.get("extension"),
                                            extension -> extension.get("type")
                                    )
                            );
                } else {
                    logger.info("No 'extensions' key found in the YAML file.");
                }
            } else {
                logger.info("Failed to load YAML data.");
            }
        } catch (IOException e) {
            logger.info("Error loading YAML file: " + e.getMessage());
        }
    }

    public String getContentType(String fileRequested) {
        String extension = getFileExtension(fileRequested);
        return extensionToTypeMap.getOrDefault("." + extension, "text/plain");
    }
}
