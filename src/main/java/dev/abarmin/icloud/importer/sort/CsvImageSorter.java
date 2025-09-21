package dev.abarmin.icloud.importer.sort;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ShellComponent
public class CsvImageSorter {

    private final List<String> imageExtensions = List.of("jpg", "heic", "gif", "png", "bmp", "jpeg");
    private final List<String> videoExtensions = List.of("mp4", "mov", "avi");
    private final String unknownDate = "0000:00:00";
    private final DateTimeFormatter exifDateFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private final DateTimeFormatter fromDetailsDateFormatter = DateTimeFormatter
            .ofPattern("EEEE MMMM d,yyyy h:mm a z", Locale.ENGLISH);

    @Autowired
    private Terminal terminal;

    @ShellMethod(
            key = "image-sort",
            value = "Sort images exported from iCloud",
            group = "iCloud import"
    )
    public void run(
            @ShellOption(value = "src", help = "Source directory, ex. /Users/test/photos") String sourceDirectoryString,
            @ShellOption(value = "dest", help = "Destination directory, ex. /Users/test/photos_sorted") String destinationDirectoryString
    ) throws Exception {
        final Path targetDirectory = Path.of(destinationDirectoryString);
        final List<Path> sources = List.of(Path.of(sourceDirectoryString));

        // check if sources directory exists
        for (Path source : sources) {
            if (!Files.exists(source)) {
                terminal.writer().println("Directory %s doesn't exist".formatted(source));
                terminal.writer().flush();
                return;
            }
        }
        // check if target directory exists
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
        }
        // get details about file locations and so on
        final Map<String, PhotoDetails> photoDetails = getPhotoDetails(sources);
        // get all images from the Photos folder
        final Map<String, Path> allPhotos = getPhotos(sources);
        // sorting files and so on
        sortImages(photoDetails, allPhotos, targetDirectory);
    }

    private void sortImages(Map<String, PhotoDetails> metadata,
                            Map<String, Path> files,
                            Path targetDirectory) {

        for (Map.Entry<String, Path> fileEntry : files.entrySet()) {
            if (metadata.containsKey(fileEntry.getKey())) {
                sortImage(fileEntry.getValue(), metadata.get(fileEntry.getKey()), targetDirectory);
            } else if (isVideo(fileEntry.getValue())) {
                toUnsortedVideos(fileEntry.getValue(), targetDirectory);
            } else {
                getDetailsFromExif(fileEntry.getValue())
                        .ifPresentOrElse(details -> {
                            sortImage(fileEntry.getValue(), details, targetDirectory);
                        }, () -> {
                            toUnsorted(fileEntry.getValue(), targetDirectory);
                        });
            }
        }
    }

    @SneakyThrows
    private void toUnsorted(Path filePath, Path targetDirectory) {
        final Path finalDirectory = targetDirectory.resolve("unsorted");
        if (!Files.exists(finalDirectory)) {
            Files.createDirectories(finalDirectory);
        }

        final Path targetFile = finalDirectory.resolve(filePath.getFileName());
        Files.move(filePath, targetFile);
    }

    @SneakyThrows
    private void toUnsortedVideos(Path filePath, Path targetDirectory) {
        final Path finalDirectory = targetDirectory.resolve("unsorted_videos");
        if (!Files.exists(finalDirectory)) {
            Files.createDirectories(finalDirectory);
        }

        final Path targetFile = finalDirectory.resolve(filePath.getFileName());
        Files.move(filePath, targetFile);
    }

    @SneakyThrows
    private Optional<PhotoDetails> getDetailsFromExif(Path imageFile) {
        final ImageMetadata metadata;
        try {
            metadata = Imaging.getMetadata(imageFile.toFile());
        } catch (Exception e) {
            log.error("Error while parsing image metadata for file {}", imageFile);
            return Optional.empty();
        }
        return getCreationDate(metadata).map(creationDate -> new PhotoDetails()
                .setFilename(imageFile.getFileName().toString())
                .setCreationDate(creationDate));
    }

    @SneakyThrows
    private Optional<LocalDate> getCreationDate(ImageMetadata metadata) {
        if (metadata instanceof JpegImageMetadata jpegMetadata) {
            final TiffField field = jpegMetadata.getExif().findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            if (field != null) {
                final String dateTimeAsString = field.getStringValue();
                final String dateAsString = StringUtils.substringBefore(dateTimeAsString, " ");
                if (unknownDate.equals(dateAsString)) {
                    return Optional.of(LocalDate.of(1970, Month.JANUARY, 1));
                }
                final LocalDate localDate = LocalDate.parse(dateAsString, exifDateFormatter);
                return Optional.of(localDate);
            }
        }
        return Optional.empty();
    }

    @SneakyThrows
    private void sortImage(Path image, PhotoDetails details, Path targetDirectory) {
        final LocalDate creationDate = details.getCreationDate();
        final Path finalDirectory = targetDirectory
                .resolve(String.valueOf(creationDate.getYear()))
                .resolve(String.format("%02d", creationDate.getMonth().getValue()));
        if (!Files.exists(finalDirectory)) {
            Files.createDirectories(finalDirectory);
        }

        // move the file there
        final Path targetFile = finalDirectory.resolve(image.getFileName());
        try {
            Files.move(image, targetFile);
        } catch (FileAlreadyExistsException e) {
            toDuplicates(image, targetDirectory);
        }
    }

    @SneakyThrows
    private void toDuplicates(Path filePath, Path targetDirectory) {
        final Path finalDirectory = targetDirectory.resolve("duplicates");
        if (!Files.exists(finalDirectory)) {
            Files.createDirectories(finalDirectory);
        }

        final Path targetFile = finalDirectory.resolve(filePath.getFileName());
        Files.move(filePath, targetFile);
    }

    private Map<String, PhotoDetails> getPhotoDetails(List<Path> sources) {
        final Map<String, PhotoDetails> result = new HashMap<>();
        for (Path source : sources) {
            result.putAll(getPhotoDetails(source));
        }
        return result;
    }

    private Map<String, Path> getPhotos(List<Path> sources) {
        final Map<String, Path> result = new HashMap<>();
        for (Path source : sources) {
            result.putAll(getPhotos(source));
        }
        return result;
    }

    @SneakyThrows
    private Map<String, Path> getPhotos(Path source) {
        final List<Path> files = Files.list(source).toList();
        final Map<String, Path> result = new HashMap<>();
        for (Path file : files) {
            if (Files.isRegularFile(file) && (isImage(file) || isVideo(file))) {
                final String filename = file.getFileName().toString();
                result.put(filename, file);
            } else if (Files.isDirectory(file)) {
                result.putAll(getPhotos(file));
            }
        }
        return result;
    }

    private boolean isImage(Path file) {
        final String filename = file.getFileName().toString();
        final String extension = StringUtils.substringAfterLast(filename, ".").toLowerCase(Locale.ROOT);
        return imageExtensions.contains(extension);
    }

    private boolean isVideo(Path file) {
        final String filename = file.getFileName().toString();
        final String extension = StringUtils.substringAfterLast(filename, ".").toLowerCase(Locale.ROOT);
        return videoExtensions.contains(extension);
    }

    @SneakyThrows
    private Map<String, PhotoDetails> getPhotoDetails(Path source) {
        final Map<String, PhotoDetails> result = new HashMap<>();
        final List<Path> children = Files.list(source).toList();
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                // going deeper
                result.putAll(getPhotoDetails(child));
            } else if (Files.isRegularFile(child) && isDetailsFile(child)) {
                // it's details file
                result.putAll(readPhotoDetailsFile(child));
            }
        }
        return result;
    }

    @SneakyThrows
    private Map<String, PhotoDetails> readPhotoDetailsFile(Path file) {
        final Map<String, PhotoDetails> result = new HashMap<>();

        final CSVParser parser = CSVParser.builder()
                .setPath(file)
                .setFormat(CSVFormat.DEFAULT.withFirstRecordAsHeader())
                .get();

        try (parser) {
            for (CSVRecord record : parser) {
                final PhotoDetails details = new PhotoDetails()
                        .setFilename(record.get(0))
                        .setCreationDate(parseDate(record.get(5), record.get(7)));

                result.put(details.getFilename(), details);
            }
        }

        return result;
    }

    private LocalDate parseDate(String creationDate, String importDate) {
        if (StringUtils.isNoneEmpty(creationDate)) {
            final ZonedDateTime parsed = ZonedDateTime.parse(creationDate, fromDetailsDateFormatter);
            return parsed.toLocalDate();
        }
        if (StringUtils.isNoneEmpty(importDate)) {
            final ZonedDateTime parsed = ZonedDateTime.parse(importDate, fromDetailsDateFormatter);
            return parsed.toLocalDate();
        }

        throw new RuntimeException("Can't get creation date");
    }

    private boolean isDetailsFile(Path path) {
        final String filename = path.getFileName().toString();
        return Strings.CI.startsWith(filename, "Photo Details") &&
                Strings.CI.endsWith(filename, ".csv");
    }

    @Data
    @Accessors(chain = true)
    class PhotoDetails {
        private String filename;
        private LocalDate creationDate;
    }
}
