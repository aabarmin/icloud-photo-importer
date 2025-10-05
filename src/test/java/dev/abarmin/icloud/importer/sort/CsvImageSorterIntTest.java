package dev.abarmin.icloud.importer.sort;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTest;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.springframework.shell.test.ShellAssertions.assertThat;

@ShellTest
class CsvImageSorterIntTest {

    @Autowired
    ShellTestClient client;

    @Test
    void imageSort_whenHelpInvoked_thenCommandAppears() {
        final ShellTestClient.NonInteractiveShellSession session = client.nonInterative("help").run();

        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(session.screen()).containsText("image-sort"));
    }

    @Test
    void imageSort_whenParametersNotProvided_thenShowsErrors() {
        final ShellTestClient.NonInteractiveShellSession session = client.nonInterative("image-sort").run();

        await()
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(session.screen())
                        .containsText("--src")
                        .containsText("--dest"));
    }

    @Test
    void imageSort_whenEmptyParametersProvided_thenShowsErrors() {
        final ShellTestClient.InteractiveShellSession session = client.interactive().run();

        session.write(
                session.writeSequence()
                        .text("image-sort --src 1 --dest 2")
                        .carriageReturn()
                        .build()
        );
        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(session.screen())
                        .containsText("Directory 1 doesn't exist"));
    }
}