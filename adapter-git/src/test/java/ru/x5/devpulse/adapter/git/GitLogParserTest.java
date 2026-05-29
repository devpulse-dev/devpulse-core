package ru.x5.devpulse.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

@DisplayName("GitLogParser: парсинг вывода git log --numstat")
class GitLogParserTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final String SHA1 = "a".repeat(40);
    private static final String SHA2 = "b".repeat(40);

    @Test
    @DisplayName("Парсит один обычный коммит с двумя файлами: суммирует строки, помечает тестовые")
    void parsesSingleCommitWithMultipleFiles() {
        List<String> lines = List.of(
                SHA1 + "|boris@x5.ru|parent1|2026-01-15T12:00:00+03:00|TASK-42 fix bug",
                "10\t5\tsrc/main/java/Foo.java",
                "20\t0\tsrc/test/java/FooTest.java"
        );

        List<Commit> commits = GitLogParser.parse(lines, REPO);

        Commit only = commits.getFirst();
        assertAll("разбор одного коммита",
                () -> assertThat(commits)
                        .as("должен распарситься ровно один коммит")
                        .hasSize(1),
                () -> assertThat(only.hash().value())
                        .as("hash сохраняется")
                        .isEqualTo(SHA1),
                () -> assertThat(only.authorEmail().value())
                        .as("email автора нормализован")
                        .isEqualTo("boris@x5.ru"),
                () -> assertThat(only.merge())
                        .as("обычный коммит — не merge")
                        .isFalse(),
                () -> assertThat(only.addedLines())
                        .as("суммарно добавлено 10 + 20")
                        .isEqualTo(30),
                () -> assertThat(only.deletedLines())
                        .as("удалено 5 + 0")
                        .isEqualTo(5),
                () -> assertThat(only.testAddedLines())
                        .as("в тестовых файлах добавлено 20 строк")
                        .isEqualTo(20),
                () -> assertThat(only.taskNumber().value())
                        .as("номер задачи извлечён из сообщения")
                        .isEqualTo("42"),
                () -> assertThat(only.repo())
                        .as("repo пришивается из аргумента parse")
                        .isEqualTo(REPO));
    }

    @Test
    @DisplayName("Помечает merge-коммит (несколько parents в поле %P)")
    void detectsMergeCommitByMultipleParents() {
        List<String> lines = List.of(
                SHA1 + "|boris@x5.ru|parent1 parent2|2026-01-15T12:00:00+03:00|Merge branch",
                "0\t0\tdummy.txt"
        );

        assertThat(GitLogParser.parse(lines, REPO).getFirst().merge())
                .as("два parent через пробел — это merge-коммит")
                .isTrue();
    }

    @Test
    @DisplayName("Пропускает бинарные файлы (numstat \"-\\t-\")")
    void skipsBinaryNumstatLines() {
        List<String> lines = List.of(
                SHA1 + "|boris@x5.ru|p|2026-01-15T12:00:00+03:00|m",
                "-\t-\timage.png",
                "10\t0\tsrc/main/java/Foo.java"
        );

        Commit commit = GitLogParser.parse(lines, REPO).getFirst();
        assertAll("бинарные файлы не должны добавлять статистику",
                () -> assertThat(commit.addedLines())
                        .as("только текстовый файл посчитан (10)")
                        .isEqualTo(10),
                () -> assertThat(commit.deletedLines())
                        .as("удалений нет")
                        .isZero());
    }

    @Test
    @DisplayName("Парсит несколько коммитов подряд и не теряет статистику предыдущего")
    void parsesMultipleCommitsInSequence() {
        List<String> lines = List.of(
                SHA1 + "|a@x5.ru|p|2026-01-01T10:00:00+03:00|first",
                "1\t1\tA.java",
                SHA2 + "|b@x5.ru|p|2026-01-02T10:00:00+03:00|second",
                "2\t2\tB.java"
        );

        List<Commit> commits = GitLogParser.parse(lines, REPO);

        assertAll("оба коммита должны быть с правильной статистикой",
                () -> assertThat(commits)
                        .as("распарсено 2 коммита")
                        .hasSize(2),
                () -> assertThat(commits.get(0).addedLines())
                        .as("первый: 1 добавленная")
                        .isEqualTo(1),
                () -> assertThat(commits.get(1).addedLines())
                        .as("второй: 2 добавленные")
                        .isEqualTo(2));
    }

    @ParameterizedTest(name = "[{index}] пустой вход")
    @NullAndEmptySource
    @DisplayName("Пустой/null вход → пустой список коммитов")
    void emptyOrNullInputProducesEmptyList(List<String> input) {
        assertThat(GitLogParser.parse(input, REPO))
                .as("на пустом входе коммитов быть не должно")
                .isEmpty();
    }

    @Test
    @DisplayName("Игнорирует коммит с невалидной датой (warn в логи, никаких исключений)")
    void skipsCommitWithInvalidDate() {
        List<String> lines = List.of(
                SHA1 + "|boris@x5.ru|p|NOT-A-DATE|m",
                "10\t5\tA.java",
                SHA2 + "|b@x5.ru|p|2026-01-15T12:00:00+03:00|ok",
                "1\t1\tB.java"
        );

        List<Commit> commits = GitLogParser.parse(lines, REPO);

        assertAll("парсер устойчив к мусорным датам",
                () -> assertThat(commits)
                        .as("остаётся только коммит с валидной датой")
                        .hasSize(1),
                () -> assertThat(commits.getFirst().hash().value())
                        .as("это второй коммит")
                        .isEqualTo(SHA2));
    }
}
