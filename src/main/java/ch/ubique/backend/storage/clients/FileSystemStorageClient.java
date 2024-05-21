package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.output.Output;
import ch.ubique.backend.storage.output.OutputWithPath;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unused", "ClassCanBeRecord"})
public class FileSystemStorageClient implements StorageClient {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemStorageClient.class);

    private final String basePath;

    public FileSystemStorageClient(final String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void write(
            final @NotNull Output output,
            final @NotNull String path,
            @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        final var file = new File(getCompletePath(path).toUri());
        final var parent = file.getParentFile();
        if (!parent.exists()) {
            synchronized (this) {
                if (!parent.exists()) {
                    logger.info("Creating parent directory {}", parent);
                    final var createdDir = parent.mkdirs();
                    if (!createdDir) {
                        throw new FileNotFoundException(
                                "Could not create directory for path " + parent);
                    }
                }
            }
        }
        logger.trace("writing output to {}", path);
        try (final var outputStream = new FileOutputStream(file)) {
            output.uncompressed().write(outputStream);
        }
    }

    @Override
    public void write(
            final Collection<OutputWithPath> outputs, @Nullable final OutputMetaData outputMetaData)
            throws IOException {
        for (final var outputWithPath : outputs) {
            write(outputWithPath.output(), outputWithPath.path(), outputMetaData);
        }
    }

    @Override
    @SuppressWarnings("java:S2095")
    public InputStream read(final @NotNull String path, final boolean doUncompressIfNeeded)
            throws IOException {
        final var file = new File(getCompletePath(path).toUri());
        logger.debug("reading input from {}", path);
        final var fileInputStream = new FileInputStream(file);

        if (doUncompressIfNeeded) {
            final var contentEncoding = FilenameUtils.getExtension(path);
            return InputStreamUtils.uncompress(fileInputStream, contentEncoding);
        }

        return fileInputStream;
    }

    @Override
    public boolean delete(final @NotNull String path, final boolean recursive) throws IOException {
        logger.debug("deleting from {}", path);
        if (recursive) {
            return FileUtils.deleteQuietly(getCompletePath(path).toFile());
        }
        return Files.deleteIfExists(getCompletePath(path));
    }

    @Override
    public Collection<String> list(@NotNull final String path, final boolean recursive)
            throws IOException {

        final var completePath = getCompletePath(path);
        if (recursive) {
            final var files =
                    FileUtils.listFiles(
                            completePath.toFile(),
                            TrueFileFilter.INSTANCE,
                            DirectoryFileFilter.DIRECTORY);
            return mapToRelativePaths(files.stream());
        }

        try (final var paths = Files.list(completePath)) {
            return mapToRelativePaths(paths.map(Path::toFile));
        }
    }

    @Override
    public boolean exists(final @NotNull String path) throws IOException {
        return Files.exists(getCompletePath(path));
    }

    private Set<String> mapToRelativePaths(final Stream<File> stream) {
        final var baseUri = Path.of(basePath).toUri();
        return stream.map(file -> baseUri.relativize(file.toURI()).getPath())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getSignedUrl(final @NotNull String path, final @NotNull OffsetDateTime expiry) {
        return getUrl(path);
    }

    @Override
    public String getUrl(final @NotNull String path) {
        return getCompletePath(path).toUri().toString();
    }

    @NotNull
    private Path getCompletePath(@NotNull final String path) {
        return Path.of(basePath, path);
    }

    public String basePath() {
        return basePath;
    }
}
