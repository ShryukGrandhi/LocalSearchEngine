package views;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import controllers.LibraryController;
import controllers.SearchController;
import models.Document;

/**
 * Use case #2. Searches as the user types (debounced) or on Enter. A newer
 * query cancels the one still running, and the results refresh by themselves
 * when documents are imported or deleted.
 */
final class SearchFrame extends JFrame {

    private static final int DEBOUNCE_MS = 250;
    private static final int TITLE_WIDTH = 220;
    private static final int SNIPPET_WIDTH = 640;
    private static final String HINT = "Type a phrase, e.g. computer algorithm. Click a result to read it.";

    private final SearchController search;
    private final LibraryController library;
    private final BiConsumer<Document, String> openReader;
    private final JTextField queryField = new JTextField(36);
    private final SearchResultTableModel results = new SearchResultTableModel();
    private final JLabel status = new JLabel(HINT);
    private final Timer debounce = new Timer(DEBOUNCE_MS, e -> runSearch());
    private final Runnable libraryListener = () -> SwingUtilities.invokeLater(this::runSearch);
    private BackgroundTask<?> running;
    private String resultsQuery = "";

    SearchFrame(SearchController search, LibraryController library, BiConsumer<Document, String> openReader) {
        super("Search documents");
        this.search = search;
        this.library = library;
        this.openReader = openReader;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        add(buildQueryBar(), BorderLayout.NORTH);
        add(buildResults(), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);
        setSize(900, 500);

        debounce.setRepeats(false);
        library.addChangeListener(libraryListener);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                debounce.stop();
                cancelRunning();
                library.removeChangeListener(libraryListener);
            }
        });
    }

    private JPanel buildQueryBar() {
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        queryField.addActionListener(e -> {
            debounce.stop();
            runSearch();
        });
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> {
            debounce.stop();
            runSearch();
        });

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bar.add(new JLabel("Search for:"), BorderLayout.WEST);
        bar.add(queryField, BorderLayout.CENTER);
        bar.add(searchButton, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane buildResults() {
        JTable table = new JTable(results);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(models.Snippet.class, new SnippetRenderer());
        table.getColumnModel().getColumn(SearchResultTableModel.TITLE).setPreferredWidth(TITLE_WIDTH);
        table.getColumnModel().getColumn(SearchResultTableModel.SNIPPET).setPreferredWidth(SNIPPET_WIDTH);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    open(row);
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        table.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (table.getSelectedRow() >= 0) {
                    open(table.getSelectedRow());
                }
            }
        });
        return new JScrollPane(table);
    }

    private void open(int row) {
        openReader.accept(results.resultAt(row).document(), resultsQuery);
    }

    private void runSearch() {
        cancelRunning();
        String raw = queryField.getText();
        if (raw.isBlank()) {
            results.setResults(java.util.List.of());
            resultsQuery = "";
            status.setText(HINT);
            return;
        }
        status.setText("Searching…");
        long started = System.nanoTime();
        running = BackgroundTask.start(cancelled -> search.search(raw, cancelled), found -> {
            resultsQuery = raw;
            results.setResults(found);
            long millis = (System.nanoTime() - started) / 1_000_000;
            status.setText(found.size() + " matching document(s) in " + millis + " ms");
        }, error -> status.setText(Dialogs.messageOf(error)));
    }

    private void cancelRunning() {
        if (running != null) {
            running.cancel(true);
            running = null;
        }
    }
}
