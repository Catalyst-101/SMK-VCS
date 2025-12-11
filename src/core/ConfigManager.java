package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_FILE = ".smk/config";
    
    public static Map<String, String> readConfig() {
        Map<String, String> cfg = new HashMap<>();
        String s;
        try {
            s = Utils.readFileStr(CONFIG_FILE);
        } catch (IOException e) {
            return cfg;
        }

        if (s.isEmpty()) return cfg;

        try (BufferedReader reader = new BufferedReader(new StringReader(s))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq == -1) continue;
                String k = line.substring(0, eq);
                String v = line.substring(eq + 1);
                cfg.put(k, v);
            }
        } catch (IOException e) {
            System.err.println("Error reading config: " + e.getMessage());
        }
        return cfg;
    }
    
    public static void writeConfig(final Map<String, String> cfg) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> kv : cfg.entrySet()) {
            out.append(kv.getKey()).append("=").append(kv.getValue()).append("\n");
        }
        try {
            Utils.writeFile(Paths.get(CONFIG_FILE), out.toString());
        } catch (IOException e) {
            System.err.println("Error writing config: " + e.getMessage());
        }
    }
}

