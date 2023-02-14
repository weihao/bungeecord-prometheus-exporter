package org.akadia.prometheus.config;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_NAME = "config.json";
    private final Map<String, String> config;

    public ConfigManager(File dataFolder) throws IOException {
        File configFile = createConfigFile(dataFolder, CONFIG_NAME, true);
        Reader reader = Files.newBufferedReader(configFile.toPath());

        Gson gson = new Gson();
        // convert JSON file to map
        config = gson.fromJson(reader, Map.class);
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public File createConfigFile(File dataFolder, String CONFIG_NAME, boolean copyFromResource) {
        File saveTo = null;
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            saveTo = new File(dataFolder, CONFIG_NAME);
            if (!saveTo.exists()) {
                if (copyFromResource) {
                    InputStream in = this.getClass().getClassLoader().getResourceAsStream(CONFIG_NAME);
                    Files.copy(in, saveTo.toPath());
                } else {
                    saveTo.createNewFile();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return saveTo;
    }
}
