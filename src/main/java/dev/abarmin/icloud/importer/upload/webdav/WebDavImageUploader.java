package dev.abarmin.icloud.importer.upload.webdav;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@ShellComponent
public class WebDavImageUploader {

    @Autowired
    private Terminal terminal;

    private final List<String> bannedNames = List.of(".DS_Store");
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @ShellMethod(
            key = "image-upload-webdav",
            value = "Upload images via WebDav",
            group = "iCloud import"
    )
    public void run(
            @ShellOption(value = "src", help = "Source directory, ex. /Users/test/photos_sorted") String sourceString,
            @ShellOption(value = "dest-url", help = "Destination WebDav URL, ex. http://hub.local/remote.php/dav/files/abc/dev") String destinationUrl,
            @ShellOption(value = "dest-login", help = "WebDav username, ex. admin") String destinationLogin,
            @ShellOption(value = "dest-password", help = "WebDav password, ex. passw0rd") String destinationPassword
    ) {
        final Path source = Path.of(sourceString);
        if (!Files.exists(source)) {
            terminal.writer().println("Source directory %s doesn't exist".formatted(source));
            terminal.writer().flush();
            return;
        }

        final Sardine sardine = SardineFactory.begin(destinationLogin, destinationPassword);
        processFolder(source, destinationUrl, sardine);
        log.info("All files processed");
    }

    @SneakyThrows
    private void processFolder(Path parent, String parentDestination, Sardine sardine) {
        log.info("Processing folder {}", parentDestination);
        final List<Path> children = Files.list(parent).toList();
        final Set<RemoteResource> existing = sardine.list(parentDestination).stream()
                .map(r -> new RemoteResource(
                        r.getName(),
                        r.getContentLength()
                ))
                .collect(Collectors.toSet());

        for (Path child : children) {
            if (isBannedFolder(child)) {
                // skip .DS_Store and other files
            } else if (Files.isDirectory(child)) {
                // check if directory exists
                final String directoryName = child.getFileName().toString();
                final String nextDestination = parentDestination + directoryName + "/";
                final RemoteResource directoryResource = new RemoteResource(directoryName, -1);
                if (!existing.contains(directoryResource)) {
                    // create a directory
                    log.info("Create directory {}", nextDestination);
                    sardine.createDirectory(nextDestination);
                }
                // process files inside
                processFolder(child, nextDestination, sardine);
            }
        }

        // process files separately and concurrently
        final List<Future<?>> futures = new ArrayList<>();
        for (Path child : children) {
            if (isBannedFolder(child)) {
                // skip
            } else if (Files.isRegularFile(child)) {
                final long fileSize = Files.size(child);
                final String fileName = child.getFileName().toString();
                final String nextFile = parentDestination + URLEncoder.encode(fileName);
                final Optional<RemoteResource> byName = getByName(existing, fileName)
                        .filter(r -> {
                            if (r.getLength() != fileSize) {
                                log.info(
                                        "Resource [{}] exists, but the size differs, existing [{}], expected [{}]",
                                        fileName,
                                        r.getLength(),
                                        fileSize
                                );
                                return false;
                            }
                            return true;
                        });
                if (byName.isEmpty()) {
                    final Future<?> future = executor.submit(() -> {
                        log.info("Upload file {}", nextFile);
                        try (final InputStream inputStream = Files.newInputStream(child)) {
                            sardine.put(nextFile, inputStream);
                        } catch (Exception e) {
                            log.error("Failed to upload file {}, will try next time", fileName, e);
                        }
                    });
                    futures.add(future);
                }
            }
        }

        // waiting for all futures to complete
        for (Future<?> future : futures) {
            future.get();
        }
    }

    private Optional<RemoteResource> getByName(Collection<RemoteResource> existing, String fileName) {
        for (RemoteResource resource : existing) {
            if (Strings.CI.equals(resource.getName(), fileName)) {
                return Optional.of(resource);
            }
            if (Strings.CI.equals(resource.getName(), fileName.replace(" ", "+"))) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    private boolean isBannedFolder(Path folder) {
        final String fileName = folder.getFileName().toString();
        return bannedNames.contains(fileName);
    }

    @Data
    static class RemoteResource {
        private final String name;
        private final long length;
    }
}
