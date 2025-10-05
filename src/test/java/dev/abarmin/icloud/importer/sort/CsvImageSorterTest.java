package dev.abarmin.icloud.importer.sort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CsvImageSorterTest {

    @InjectMocks
    CsvImageSorter sorter;

    @Test
    void moveAvoidingDuplicates_whenNoFileExists_shouldJustMove() throws Exception {
        final Path targetDirectory = Files.createTempDirectory("target");
        final Path sourceFile = Files.createTempFile("name", ".jpg");

        final Path result = sorter.moveAvoidingDuplicates(sourceFile, targetDirectory);

        assertThat(result).exists();
        assertThat(result).hasParent(targetDirectory);
    }

    @Test
    void moveAvoidingDuplicates_whenFileExists_shouldAddNumbers() throws Exception {
        final Path targetDirectory = Files.createTempDirectory("target");
        final Path sourceFile = Files.createTempFile("name", ".jpg");
        final Path existingCopy = targetDirectory.resolve(sourceFile.getFileName());

        Files.copy(sourceFile, existingCopy);

        final Path result = sorter.moveAvoidingDuplicates(sourceFile, targetDirectory);

        assertThat(result).exists();
        assertThat(existingCopy).exists();
        assertThat(result).hasParent(targetDirectory);
    }
}