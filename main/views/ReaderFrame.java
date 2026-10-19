package views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.PlainDocument;

import controllers.ExportController;
import controllers.ReaderController;
import models.Document;
import models.IndexedText;
import models.Match;
import models.MatchCursor;
import models.OpenedDocument;

/**
 * Use case #4: a non-modal frame showing one document with every match
 * highlighted, a match counter, circular Previous/Next, Download (use case #5)
 * and Close. Several can be open at once.
 *
 * <p>Large documents stay responsive: the Swing text model is built off the
 * event thread, and highlights are added in small batches.
 */
final class ReaderFrame extends JFrame {

    /** Wrapped layout runs on the EDT at ~0.65 ms per KB; above this size wrapping is turned off. */
    private static final int WRAP_LIMIT_CHARS = 100_000;
    private static final int HIGHLIGHT_BATCH = 2_000;
    private static final Highlighter.HighlightPainter ALL_MATCHES =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFFF07A));
    private static final Highlighter.HighlightPainter CURRENT_MATCH =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF9F1C));

    private final ReaderController reader;
    private final ExportController exporter;
    private final Document document;
    private final JTextField findField;
    private final JLabel matchLabel = new JLabel(" ");
    private final JTextArea textArea = new JTextArea();
    private final JButton previousButton = new JButton("◀ Previous");
    private final JButton nextButton = new JButton("Next ▶");
    private final JButton downloadButton = new JButton("Download…");
    private final JButton findButton = new JButton("Find");

    private IndexedText text;
    private MatchCursor cursor = MatchCursor.of(List.of());
    private Object[] highlightTags = new Object[0];
    private Object currentTag;
    private int highlightGeneration;
    private BackgroundTask<?> loading;
    private BackgroundTask<?> finding;

    private record Loaded(OpenedDocument opened, PlainDocument model) {
    }

    ReaderFrame(ReaderController reader, ExportController exporter, Document document, String query) {
        super(document.title());
        this.reader = reader;
        this.exporter = exporter;
        this.document = document;
        this.findField = new JTextField(query, 28);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setMargin(new java.awt.Insets(8, 10, 8, 10));

        add(buildFindBar(), BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        setSize(820, 640);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                highlightGeneration++;
                cancel(loading);
                cancel(finding);
            }
        });
    }

    void open() {
        setVisible(true);
        setControlsEnabled(false);
        matchLabel.setText("Loading…");
        String query = findField.getText();
        loading = BackgroundTask.start(cancelled -> {
            OpenedDocument opened = reader.open(document.id(), query);
            PlainDocument model = new PlainDocument();
            model.insertString(0, OpenedDocument.displayText(opened.text().original()), null);
            return new Loaded(opened, model);
        }, this::show, error -> {
            Dialogs.showError(this, "Could not open \"" + document.title() + "\"", error);
            dispose();
        });
    }

    private JPanel buildFindBar() {
        findField.addActionListener(e -> find());
        findButton.addActionListener(e -> find());
        matchLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Find:"));
        bar.add(findField);
        bar.add(findButton);
        bar.add(matchLabel);
        return bar;
    }

    private JPanel buildButtons() {
        previousButton.addActionListener(e -> move(MatchCursor::previous));
        nextButton.addActionListener(e -> move(MatchCursor::next));
        downloadButton.addActionListener(e -> download());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(previousButton);
        buttons.add(nextButton);
        buttons.add(downloadButton);
        buttons.add(closeButton);
        return buttons;
    }

    private void show(Loaded loaded) {
        text = loaded.opened().text();
        textArea.setLineWrap(text.length() <= WRAP_LIMIT_CHARS);
        textArea.setDocument(loaded.model());
        setControlsEnabled(true);
        applyMatches(loaded.opened().matches());
    }

    private void find() {
        if (text == null) {
            return;
        }
        IndexedText current = text;
        String raw = findField.getText();
        cancel(finding); // the newest query wins; a cancelled one never reports back
        finding = BackgroundTask.start(cancelled -> reader.find(current, raw), this::applyMatches,
                error -> Dialogs.showError(this, "Find failed", error));
    }

    private static void cancel(BackgroundTask<?> task) {
        if (task != null) {
            task.cancel(true);
        }
    }

    private void applyMatches(List<Match> matches) {
        textArea.getHighlighter().removeAllHighlights();
        cursor = MatchCursor.of(matches);
        highlightTags = new Object[matches.size()];
        currentTag = null;
        int generation = ++highlightGeneration;
        showCurrent();
        addHighlightBatch(matches, 0, generation);
    }

    /** Adds one batch of highlights, then yields to the event queue before the next. */
    private void addHighlightBatch(List<Match> matches, int from, int generation) {
        if (generation != highlightGeneration) {
            return;
        }
        int to = Math.min(matches.size(), from + HIGHLIGHT_BATCH);
        for (int i = from; i < to; i++) {
            if (i != cursor.index() && highlightTags[i] == null) {
                highlightTags[i] = addHighlight(matches.get(i), ALL_MATCHES);
            }
        }
        if (to < matches.size()) {
            SwingUtilities.invokeLater(() -> addHighlightBatch(matches, to, generation));
        }
    }

    private void move(UnaryOperator<MatchCursor> step) {
        int previous = cursor.index();
        cursor = step.apply(cursor);
        if (previous >= 0 && previous != cursor.index()) {
            highlightTags[previous] = addHighlight(cursor.matches().get(previous), ALL_MATCHES);
        }
        showCurrent();
    }

    /** Swaps the current match to the "current" colour, updates the counter and scrolls to it. */
    private void showCurrent() {
        Highlighter highlighter = textArea.getHighlighter();
        if (currentTag != null) {
            highlighter.removeHighlight(currentTag);
            currentTag = null;
        }
        boolean hasMatches = !cursor.isEmpty();
        previousButton.setEnabled(hasMatches);
        nextButton.setEnabled(hasMatches);
        if (!hasMatches) {
            matchLabel.setText(findField.getText().isBlank() ? " " : "No matches");
            return;
        }
        matchLabel.setText("Match " + cursor.position() + " of " + cursor.count());
        int index = cursor.index();
        if (highlightTags[index] != null) {
            highlighter.removeHighlight(highlightTags[index]);
            highlightTags[index] = null;
        }
        Match match = cursor.matches().get(index);
        currentTag = addHighlight(match, CURRENT_MATCH);
        scrollTo(match);
    }

    private Object addHighlight(Match match, Highlighter.HighlightPainter painter) {
        try {
            return textArea.getHighlighter().addHighlight(match.start(), match.end(), painter);
        } catch (BadLocationException e) {
            return null;
        }
    }

    private void scrollTo(Match match) {
        textArea.setCaretPosition(match.start());
        try {
            Rectangle2D view = textArea.modelToView2D(match.start());
            if (view != null) {
                Rectangle visible = textArea.getVisibleRect();
                int y = Math.max(0, (int) view.getY() - visible.height / 2);
                textArea.scrollRectToVisible(new Rectangle(0, y, 1, visible.height));
            }
        } catch (BadLocationException e) {
            // offset came from this same text, so this cannot happen; leave the view where it is
        }
    }

    // ---- use case #5: download ----

    private void download() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Download \"" + document.title() + "\"");
        chooser.setSelectedFile(new File(ExportController.suggestFileName(document.title())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                target.getFileName() + " already exists. Replace it?", "Confirm download",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        String content = text.original();
        downloadButton.setEnabled(false);
        BackgroundTask.start(cancelled -> {
            exporter.export(content, target);
            return target;
        }, saved -> {
            downloadButton.setEnabled(true);
            Dialogs.showInfo(this, "Download complete", "Saved to " + saved.toAbsolutePath());
        }, error -> {
            downloadButton.setEnabled(true);
            Dialogs.showError(this, "Download failed", error);
        });
    }

    private void setControlsEnabled(boolean enabled) {
        findField.setEnabled(enabled);
        findButton.setEnabled(enabled);
        downloadButton.setEnabled(enabled);
        previousButton.setEnabled(enabled && !cursor.isEmpty());
        nextButton.setEnabled(enabled && !cursor.isEmpty());
    }
}
