package core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.Map;

public class IndexManager {

    private static final String INDEX_FILE = ".smk/index";

    public static void writeIndex(final IndexMap idx) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> kv : idx.entrySet()) {
            out.append(kv.getKey()).append("\t").append(kv.getValue()).append("\n");
        }

        try {
            // Assumed core.Utils.writeFile exists
            Utils.writeFile(Paths.get(INDEX_FILE), out.toString());
        } catch (IOException e) {
            System.err.println("Error writing index file: " + e.getMessage());
        }
    }

    public static IndexMap readIndex() {
        IndexMap idx = new IndexMap();
        String s;
        try {
            // Assumed core.Utils.readFileStr exists
            s = Utils.readFileStr(INDEX_FILE);
        } catch (IOException e) {
            // Treat file not found/error as an empty index
            return idx;
        }

        if (s.isEmpty()) return idx;

        try (BufferedReader reader = new BufferedReader(new StringReader(s))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int tab = line.indexOf('\t');

                if (tab == -1) continue;

                String path = line.substring(0, tab);
                String hash = line.substring(tab + 1);

                idx.put(path, hash);
            }
        } catch (IOException e) {
            System.err.println("Error processing index file content: " + e.getMessage());
        }

        return idx;
    }
}