package ru.x5.devpulse.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.x5.devpulse.domain.model.git.Commit;
import ru.x5.devpulse.domain.model.git.RepoName;

@DisplayName("GitLogParser.Streaming: push-stream state machine")
class GitLogParserStreamingTest {

    private static final RepoName REPO = new RepoName("xrg-core");
    private static final String SHA1 = "a".repeat(40);
    private static final String SHA2 = "b".repeat(40);
    private static final String SHA3 = "c".repeat(40);

    @Test
    @DisplayName("Один коммит: sink дёргается ровно один раз после finish()")
    void singleCommitEmittedOnFinish() {
        List<Commit> sink = new ArrayList<>();
        GitLogParser.Streaming parser = new GitLogParser.Streaming(REPO, sink::add);

        parser.onLine(SHA1 + "|boris@x5.ru|p|2026-01-15T12:00:00+03:00|TASK-42 fix");
        parser.onLine("10\t5\tsrc/main/java/Foo.java");

        assertAll("до finish sink пуст; после — один коммит",
                () -> assertThat(sink).as("до finish ещё ничего не эмитнули").isEmpty(),
                () -> {
                    parser.finish();
                    assertThat(sink).hasSize(1);
                    assertThat(sink.getFirst().addedLines()).isEqualTo(10);
                });
    }

    @Test
    @DisplayName("Несколько коммитов: каждый эмитится при появлении следующего header'а")
    void multipleCommitsStreamedAsTheyComplete() {
        List<Commit> sink = new ArrayList<>();
        GitLogParser.Streaming parser = new GitLogParser.Streaming(REPO, sink::add);

        parser.onLine(SHA1 + "|a@x5.ru|p|2026-01-01T10:00:00+03:00|first");
        parser.onLine("1\t1\tA.java");
        parser.onLine(""); // разделитель — игнорируется
        // в этот момент первый коммит ЕЩЁ не эмитнут — он эмитится при header'е следующего
        assertThat(sink).as("первый коммит ждёт следующего header").isEmpty();

        parser.onLine(SHA2 + "|b@x5.ru|p|2026-01-02T10:00:00+03:00|second");
        // как только пришёл header второго — первый flush'ится
        assertThat(sink).as("первый коммит эмитнут при header второго").hasSize(1);

        parser.onLine("2\t2\tB.java");
        parser.onLine(SHA3 + "|c@x5.ru|p|2026-01-03T10:00:00+03:00|third");
        assertThat(sink).as("теперь два коммита, третий ещё в работе").hasSize(2);

        parser.finish();
        assertThat(sink).as("finish() эмитит последний").hasSize(3);
    }

    @Test
    @DisplayName("finish() без коммитов — sink остаётся пустым (никаких NPE)")
    void finishOnEmptyParserIsSafe() {
        List<Commit> sink = new ArrayList<>();
        GitLogParser.Streaming parser = new GitLogParser.Streaming(REPO, sink::add);

        parser.finish();

        assertThat(sink).isEmpty();
    }

    @Test
    @DisplayName("Мусорные строки до первого header игнорируются")
    void garbageBeforeFirstHeaderIgnored() {
        List<Commit> sink = new ArrayList<>();
        GitLogParser.Streaming parser = new GitLogParser.Streaming(REPO, sink::add);

        parser.onLine("какая-то ерунда без табов и без @");
        parser.onLine("");
        parser.onLine("ещё одна");
        parser.onLine(SHA1 + "|boris@x5.ru|p|2026-01-15T12:00:00+03:00|m");
        parser.onLine("1\t1\tA.java");
        parser.finish();

        assertThat(sink).hasSize(1);
    }

    @Test
    @DisplayName("Коммит с невалидной датой отбрасывается, следующий парсится нормально")
    void invalidDateDoesNotPoisonStream() {
        List<Commit> sink = new ArrayList<>();
        GitLogParser.Streaming parser = new GitLogParser.Streaming(REPO, sink::add);

        parser.onLine(SHA1 + "|boris@x5.ru|p|NOT-A-DATE|m");
        parser.onLine("10\t5\tA.java");
        parser.onLine(SHA2 + "|b@x5.ru|p|2026-01-15T12:00:00+03:00|ok");
        parser.onLine("1\t1\tB.java");
        parser.finish();

        assertAll("остался только валидный",
                () -> assertThat(sink).hasSize(1),
                () -> assertThat(sink.getFirst().hash().value()).isEqualTo(SHA2));
    }
}
