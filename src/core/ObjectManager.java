package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Stores and retrieves content-addressed objects (blobs, trees, commits) in .smk/objects.
 */
public class ObjectManager {

    private static final String VCS_DIR = ".smk";
    private static final String OBJECTS_DIR = VCS_DIR + "/objects";

    /**
     * Represents the content read from an object file.
     * @param type The object type ("blob", "tree", "commit").
     * @param content The raw content of the object (excluding header).
     */

    public record ObjectContent(String type, String content) {}

    /**
     * Calculates a simple hash for the content.
     * Note: This uses Java's built-in String hashCode for simplicity,
     * but a real VCS would use a secure hash like SHA-1 or SHA-256 (e.g., using MessageDigest).
     * @param data The full content string (including header).
     * @return A hexadecimal string representation of the hash.
     */
    private static String calculateContentHash(final String data) {
        // C++ std::hash<std::string> is translated here to Java's String.hashCode()
        // and then converted to a fixed-width hexadecimal string.
        long h = Objects.hashCode(data) & 0xFFFFFFFFL; // Use long to ensure positive value for hexadecimal
        return String.format("%016x", h);
    }

    /**
     * Hashes the object content, stores the full object (header + content) in the object store,
     * and returns the hash ID.
     * @param type The object type ("blob", "tree", "commit").
     * @param content The raw content string.
     * @return The calculated hash ID.
     */
    public static String hashAndStoreObject(final String type, final String content) {
        String header = type + "\n" + content.length() + "\n";
        String full = header + content;
        String id = calculateContentHash(full);

        Path path = Paths.get(OBJECTS_DIR, id);

        try {
            if (!Files.exists(path)) {
                Utils.writeFile(path, full);
            }
        } catch (IOException e) {
            System.err.println("Error storing object " + id + ": " + e.getMessage());
        }

        return id;
    }

    /**
     * Reads an object from the object store using its ID.
     * @param id The hash ID of the object.
     * @return An ObjectContent record containing the type and content, or empty if not found.
     */
    public static ObjectContent readObjectContent(final String id) {
        String obj;
        try {
            obj = Utils.readFileStr(OBJECTS_DIR + "/" + id);
        } catch (IOException e) {
            return new ObjectContent("", "");
        }

        if (obj.isEmpty()) return new ObjectContent("", "");

        int p1 = obj.indexOf('\n');
        if (p1 == -1) return new ObjectContent("", "");

        int p2 = obj.indexOf('\n', p1 + 1);
        if (p2 == -1) return new ObjectContent("", "");

        String type = obj.substring(0, p1);
        // The length header is discarded, only the type and content are returned.
        String content = obj.substring(p2 + 1);

        return new ObjectContent(type, content);
    }

    /**
     * Reads a file from the working directory, creates a "blob" object from its content,
     * stores it, and returns the blob's ID.
     * @param filePath The path to the file in the working directory.
     * @return The hash ID of the new blob object.
     */
    public static String hashBlobFromFile(final String filePath) throws IOException {
        String content = Utils.readFileStr(filePath);
        return hashAndStoreObject("blob", content);
    }
}