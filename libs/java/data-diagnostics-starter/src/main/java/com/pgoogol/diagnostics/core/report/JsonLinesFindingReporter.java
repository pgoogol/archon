package com.pgoogol.diagnostics.core.report;

import com.pgoogol.diagnostics.core.UnitOfWork;
import com.pgoogol.diagnostics.core.UnitOfWorkSummary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Wnioski w pliku JSONL, jedna linia na wniosek w formacie {@link FindingJsonWriter}.
 * Plik da się przeczytać narzędziem albo wkleić do czatu bez obróbki.
 *
 * <p>Zapis idzie pod blokadą, więc linie z kilku wątków się nie przeplatają. Gdy plik
 * przekroczyłby limit rozmiaru, przechodzi na {@code <nazwa>.1} (poprzedni {@code .1}
 * znika) i zapis zaczyna się od pustego pliku. Blokada działa w obrębie jednej JVM; dwie
 * aplikacje nie powinny pisać do tego samego pliku.</p>
 *
 * <p>Błąd zapisu wychodzi jako {@link UncheckedIOException}. Silnik łapie go i nie zabiera
 * raportu pozostałym reporterom.</p>
 */
public class JsonLinesFindingReporter implements FindingReporter {

    public static final Path DEFAULT_PATH = Path.of("target", "data-diagnostics", "findings.jsonl");

    public static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    private final Path path;

    private final long maxBytes;

    private final FindingJsonWriter writer;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param path     plik wniosków; brakujące katalogi powstają przy pierwszym zapisie
     * @param maxBytes rozmiar, po którym plik przechodzi na {@code .1}
     * @param writer   zapis wniosku jako linii JSON
     */
    public JsonLinesFindingReporter(Path path, long maxBytes, FindingJsonWriter writer) {

        this.path = Objects.requireNonNull(path, "ścieżka pliku wniosków jest wymagana");
        if (maxBytes < 1) {

            throw new IllegalArgumentException("limit rozmiaru pliku wniosków musi być dodatni: " + maxBytes);
        }
        this.maxBytes = maxBytes;
        this.writer = Objects.requireNonNull(writer, "zapis JSON jest wymagany");
    }

    @Override
    public void report(UnitOfWork unit, List<Finding> findings) {

        if (findings.isEmpty()) {

            return;
        }
        UnitOfWorkSummary summary = unit.summary();
        String lines = findings.stream()
            .map(finding -> writer.write(finding, summary) + "\n")
            .collect(Collectors.joining());
        byte[] bytes = lines.getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {

            append(bytes);
        } catch (IOException failure) {

            throw new UncheckedIOException("nie udał się zapis wniosków do " + path, failure);
        } finally {

            lock.unlock();
        }
    }

    public Path path() {

        return path;
    }

    private void append(byte[] bytes) throws IOException {

        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        rotateIfFull(bytes.length);
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Pusty plik nie przechodzi na {@code .1}: zapis większy niż limit i tak musi gdzieś trafić. */
    private void rotateIfFull(int incomingBytes) throws IOException {

        if (Files.notExists(path)) {

            return;
        }
        long size = Files.size(path);
        if (size == 0 || size + incomingBytes <= maxBytes) {

            return;
        }
        Path rotated = path.resolveSibling(path.getFileName() + ".1");
        Files.move(path, rotated, StandardCopyOption.REPLACE_EXISTING);
    }
}
