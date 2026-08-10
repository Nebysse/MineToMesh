package com.onecuber.mcgltf.texture;

import com.onecuber.mcgltf.scene.TextureKey;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

public final class TextureRegistry {
    private final Map<TextureKey, Entry> entries = new LinkedHashMap<>();

    public int register(TextureKey key, TextureImage image) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(image, "image");
        Entry existing = entries.get(key);
        if (existing != null) {
            return existing.index();
        }
        int index = entries.size();
        entries.put(key, new Entry(index, key, image));
        return index;
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public void writeAll(Path transactionRoot) throws IOException {
        Path root = Objects.requireNonNull(transactionRoot, "transactionRoot")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Transaction root does not exist: " + root);
        }
        for (Entry entry : entries.values()) {
            Path output = contained(root, entry.key().outputPath());
            Files.createDirectories(output.getParent());
            if (entry.image().sourcePng().isPresent() && entry.image().animation().isEmpty()) {
                Files.write(output, entry.image().sourcePng().orElseThrow(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } else {
                writePng(output, entry.image());
            }
            writeAnimationSource(root, entry);
        }
    }

    private static void writeAnimationSource(Path root, Entry entry) throws IOException {
        if (entry.image().sourcePng().isEmpty() || entry.image().animation().isEmpty()) {
            return;
        }
        String sourceId = entry.key().sourceId();
        int separator = sourceId.indexOf(':');
        String namespace = separator >= 0 ? sourceId.substring(0, separator) : "minecraft";
        String path = separator >= 0 ? sourceId.substring(separator + 1) : sourceId;
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        Path source = contained(root, "textures/source/" + namespace + "/" + path + ".png");
        Files.createDirectories(source.getParent());
        Files.write(source, entry.image().sourcePng().orElseThrow(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if (entry.image().mcmeta().isPresent()) {
            Files.write(Path.of(source.toString() + ".mcmeta"), entry.image().mcmeta().orElseThrow(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    private static void writePng(Path output, TextureImage image) throws IOException {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        byte[] rgba = image.rgba();
        int cursor = 0;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int red = rgba[cursor++] & 0xFF;
                int green = rgba[cursor++] & 0xFF;
                int blue = rgba[cursor++] & 0xFF;
                int alpha = rgba[cursor++] & 0xFF;
                buffered.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        if (!ImageIO.write(buffered, "PNG", output.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }

    private static Path contained(Path root, String relativePath) throws IOException {
        Path relative = Path.of(relativePath);
        Path output = root.resolve(relative).normalize();
        if (relative.isAbsolute() || !output.startsWith(root)) {
            throw new IOException("Texture path escapes transaction root: " + relativePath);
        }
        return output;
    }

    public record Entry(int index, TextureKey key, TextureImage image) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(image, "image");
        }
    }
}
