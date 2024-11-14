package ch.ubique.backend.storage.clients;

import ch.ubique.backend.core.utils.InputStreamUtils;
import ch.ubique.backend.storage.OutputMetaData;
import ch.ubique.backend.storage.StorageClient;
import ch.ubique.backend.storage.exception.StorageClientException;
import ch.ubique.backend.storage.output.Output;
import ch.ubique.backend.storage.output.OutputWithPath;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.text.MessageFormat;
import org.apache.commons.io.FileUtils;
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
            throws StorageClientException {
        final var completePath = getCompletePath(path);
        final var file = new File(completePath.toUri());
        final var parent = file.getParentFile();
        if (!parent.exists()) {
            synchronized (this) {
                if (!parent.exists()) {
                    logger.info("Creating parent directory {}", parent);
                    final var createdDir = parent.mkdirs();
                    if (!createdDir) {
                        throw StorageClientException.of(
                                "Could not create directory for path " + parent,
                                new FileNotFoundException());
                    }
                }
            }
        }
        logger.trace("writing output to {} (complete path {})", path, completePath);
        try (final var outputStream = new FileOutputStream(file)) {
            output.uncompressed().write(outputStream);
        } catch (final IOException e) {
            throw StorageClientException.of(
                    MessageFormat.format("failed to write for [path={0}]", path), e);
        }
    }

    @Override
    public void write(
            final Collection<OutputWithPath> outputs, @Nullable final OutputMetaData outputMetaData)
            throws StorageClientException {
        for (final var outputWithPath : outputs) {
            write(outputWithPath.output(), outputWithPath.path(), outputMetaData);
        }
    }

    @Override
    @SuppressWarnings("java:S2095")
    public InputStream read(final @NotNull String path, final boolean doUncompressIfNeeded)
            throws StorageClientException {
        final var completePath = getCompletePath(path);
        final var file = new File(completePath.toUri());
        logger.debug("reading input from {} (complete path {})", path, completePath);

        try {
            final var fileInputStream = new FileInputStream(file);

            if (doUncompressIfNeeded) {
                return InputStreamUtils.uncompress(fileInputStream, null, path);
            }

            return fileInputStream;
        } catch (final IOException e) {
            throw StorageClientException.of(MessageFormat.format("failed to read {0}", file), e);
        }
    }

    @Override
    public boolean delete(final @NotNull String path, final boolean recursive)
            throws StorageClientException {
        final var completePath = getCompletePath(path);
        logger.debug("deleting from {} (complete path {})", path, completePath);
        if (recursive) {
            return FileUtils.deleteQuietly(completePath.toFile());
        }
        try {
            return Files.deleteIfExists(completePath);
        } catch (final IOException e) {
            throw StorageClientException.of(
                    MessageFormat.format("failed to delete for [path={0}, recursive=false]", path),
                    e);
        }
    }

    @Override
    public Collection<String> list(@NotNull final String path, final boolean recursive)
            throws StorageClientException {
        final var completePath = getCompletePath(path);
        logger.debug("listing files from {} (complete path {})", path, completePath);
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
        } catch (final IOException e) {
            throw StorageClientException.of(
                    MessageFormat.format("failed to list for [path={0}, recursive=false]", path),
                    e);
        }
    }

    @Override
    public boolean exists(final @NotNull String path) {
        final var completePath = getCompletePath(path);
        logger.debug("checking file existing at {} (complete path {})", path, completePath);
        return Files.exists(completePath.toFile().toPath());
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
