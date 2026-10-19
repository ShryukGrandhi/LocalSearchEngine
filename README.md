# Local Search Engine

A desktop app (Java 17 + Swing) for importing plain-text ASCII documents and searching them by phrase. The interface stays responsive the whole time.

- **Import** text files (File → Import…). A read-only copy is stored under a title you choose.
- **Search** every document as you type (Search → Search By…). Matching is case-insensitive, and each result shows a snippet around the first match.
- **Manage** documents in a table ordered by title, with a 500-character preview. Tick documents and delete them after a confirmation.
- **Read** documents in separate windows, with every match highlighted, a match counter, and circular Previous/Next.
- **Download** any document to a path you choose.

The design document, with UML class and sequence diagrams, is in [`design/DESIGN.md`](design/DESIGN.md).

## Requirements

- JDK 17 or newer (`java` and `javac` on `PATH`)
- `curl` (only the first time you run the tests, to download JUnit)

## Build, test and run

| | macOS / Linux / Git Bash | Windows `cmd` |
|---|---|---|
| Build the jar | `bash build/build.sh` | `build\build.cmd` |
| Run all tests | `bash build/test.sh` | `build\test.cmd` |
| Start the app | `bash build/run.sh` | `build\run.cmd` |
| Coverage report | `bash build/coverage.sh` → `out/coverage/index.html` | – |

After building you can also run `java -jar out/local-search-engine.jar` directly.

The library is stored in `~/.localsearch`. To use another folder, for example for a demo:

```bash
JAVA_OPTS="-Dlocalsearch.home=/tmp/demo-library" bash build/run.sh
```

## Trying it out

1. **File → Import…** and pick `test/data/algorithms.txt`. Keep the suggested title. Import `databases.txt` and `networking.txt` too.
2. **Search → Search By…** and type `computer algorithm`. Only *algorithms* matches: `networking.txt` contains `Computer_Algorithm` and `Computer  Algorithm`, which the spec says must not match.
3. Click the result. A reader window opens with the 3 matches highlighted. Use **Next ▶** / **◀ Previous**; both wrap around.
4. Click **Download…**, choose a location, and check that the saved file is identical to the original.
5. Back in the main window, tick **Delete** on some documents and click **Delete selected…**.

## Repository layout

```
design/   Design document (Markdown + Mermaid UML)
main/     Source code
  app/          entry point / wiring
  models/       data, storage, index, matching rules
  controllers/  one controller per group of use cases
  views/        Swing frames and dialogs
test/     JUnit 5 tests (same packages as main/) and test/data sample files
build/    Build, test, run and coverage scripts (.sh and .cmd)
```

## Tests

89 JUnit 5 tests cover the matching rules (every example in the spec), snippets, title and encoding policies, storage (persistence, replacement, read-only copies, crash-safe catalog), the trigram index, the controllers end to end, CRLF offset handling, byte-identical downloads, and a performance check (~33 MB corpus searched in under 500 ms). Line coverage on `models` + `controllers` is about 90%.
