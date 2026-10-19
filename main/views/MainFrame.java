package views;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import controllers.ExportController;
import controllers.LibraryController;
import controllers.ReaderController;
import controllers.SearchController;
import models.Document;
import models.InvalidDocumentException;
import models.TitlePolicy;

/**
 * The main frame (use case #3): every document in a table ordered by title,
 * with File (Import, Exit) and Search (Search By) menus.
 */
public final class MainFrame extends JFrame {

    private static final int TITLE_WIDTH = 220;
    private static final int PREVIEW_WIDTH = 640;
    private static final int DELETE_WIDTH = 60;
    private static final int CASCADE_STEP = 30;

    private final LibraryController library;
    private final SearchController search;
    private final ReaderController reader;
    private final ExportController exporter;
    private final DocumentTableModel tableModel = new DocumentTableModel();
    private final JLabel status = new JLabel(" ");
    private final JProgressBar busy = new JProgressBar();
    private int runningTasks;
    private int refreshGeneration;
    private int openedReaders;
    private File lastDirectory;
    private SearchFrame searchFrame;

    public MainFrame(LibraryController library, SearchController search,
                     ReaderController reader, ExportController exporter) {
        super("Local Search Engine");
        this.library = library;
        this.search = search;
        this.reader = reader;
        this.exporter = exporter;

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        add(buildTable(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        library.addChangeListener(() -> SwingUtilities.invokeLater(this::refresh));
    }

    /** Loads the stored documents into the index without blocking the window. */
    public void loadLibrary() {
        beginTask("Loading library…");
        BackgroundTask.start(cancelled -> library.loadIndex(), failed -> {
            endTask();
            if (!failed.isEmpty()) {
                Dialogs.showInfo(this, "Some documents could not be loaded",
                        "These documents could not be read and are not searchable:\n" + String.join("\n", failed));
            }
            refresh();
        }, error -> {
            endTask();
            Dialogs.showError(this, "Could not load library", error);
        });
    }

    private JMenuBar buildMenuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenuItem importItem = new JMenuItem("Import…", KeyEvent.VK_I);
        importItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut));
        importItem.addActionListener(e -> importDocument());
        JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcut));
        exitItem.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(importItem);
        file.addSeparator();
        file.add(exitItem);

        JMenuItem searchBy = new JMenuItem("Search By…", KeyEvent.VK_B);
        searchBy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcut));
        searchBy.addActionListener(e -> openSearch());
        JMenu searchMenu = new JMenu("Search");
        searchMenu.setMnemonic(KeyEvent.VK_S);
        searchMenu.add(searchBy);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(searchMenu);
        return bar;
    }

    private JScrollPane buildTable() {
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(DocumentTableModel.TITLE).setPreferredWidth(TITLE_WIDTH);
        table.getColumnModel().getColumn(DocumentTableModel.PREVIEW).setPreferredWidth(PREVIEW_WIDTH);
        table.getColumnModel().getColumn(DocumentTableModel.DELETE).setMaxWidth(DELETE_WIDTH);
        table.setToolTipText("Double-click a document to read it");
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0 && column != DocumentTableModel.DELETE) {
                    openReader(tableModel.documentAt(row), "");
                }
            }
        });
        return new JScrollPane(table);
    }

    private JPanel buildFooter() {
        JButton deleteButton = new JButton("Delete selected…");
        deleteButton.addActionListener(e -> deleteChecked());
        busy.setIndeterminate(true);
        busy.setVisible(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(status);
        left.add(busy);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(deleteButton);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        footer.add(left, BorderLayout.CENTER);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    private void refresh() {
        int generation = ++refreshGeneration;
        BackgroundTask.start(cancelled -> library.listDocuments(), documents -> {
            if (generation != refreshGeneration) {
                return; // a newer refresh is running; this list may be stale
            }
            tableModel.setDocuments(documents);
            if (runningTasks == 0) {
                status.setText(documents.size() + " document(s) in the library");
            }
        }, error -> Dialogs.showError(this, "Could not list documents", error));
    }

    // ---- use case #1: import ----

    private void importDocument() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle("Import document");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastDirectory = file.getParentFile();
        promptTitle(defaultTitle(file)).ifPresent(title -> startImport(file.toPath(), title));
    }

    /** Asks for a title until it is valid; confirms before replacing an existing document. */
    private Optional<String> promptTitle(String initial) {
        String proposal = initial;
        while (true) {
            Object answer = JOptionPane.showInputDialog(this, "Title for this document:", "Import document",
                    JOptionPane.QUESTION_MESSAGE, null, null, proposal);
            if (answer == null) {
                return Optional.empty();
            }
            proposal = answer.toString();
            String title;
            try {
                title = TitlePolicy.validate(proposal);
            } catch (InvalidDocumentException e) {
                Dialogs.showError(this, "Invalid title", e);
                continue;
            }
            if (!library.titleExists(title)) {
                return Optional.of(title);
            }
            int choice = JOptionPane.showConfirmDialog(this,
                    "A document titled \"" + title + "\" already exists.\nReplace it with this file?",
                    "Replace document", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                return Optional.of(title);
            }
            if (choice != JOptionPane.NO_OPTION) {
                return Optional.empty();
            }
        }
    }

    private void startImport(Path file, String title) {
        beginTask("Importing \"" + title + "\"…");
        BackgroundTask.start(cancelled -> library.importFile(file, title), document -> {
            endTask();
            status.setText("Imported \"" + document.title() + "\"");
        }, error -> {
            endTask();
            Dialogs.showError(this, "Import failed", error);
        });
    }

    private static String defaultTitle(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---- use case #3: delete ----

    private void deleteChecked() {
        List<Document> checked = tableModel.checkedDocuments();
        if (checked.isEmpty()) {
            Dialogs.showInfo(this, "Nothing to delete", "Tick the Delete box next to the documents you want to remove.");
            return;
        }
        if (!DeleteConfirmDialog.confirm(this, checked)) {
            return;
        }
        List<String> ids = checked.stream().map(Document::id).toList();
        beginTask("Deleting " + ids.size() + " document(s)…");
        BackgroundTask.start(cancelled -> {
            library.delete(ids);
            return ids.size();
        }, count -> {
            endTask();
            status.setText("Deleted " + count + " document(s)");
        }, error -> {
            endTask();
            Dialogs.showError(this, "Delete failed", error);
        });
    }

    // ---- use cases #2 and #4 ----

    private void openSearch() {
        if (searchFrame == null || !searchFrame.isDisplayable()) {
            searchFrame = new SearchFrame(search, library, this::openReader);
            searchFrame.setLocation(cascade(1));
        }
        searchFrame.setVisible(true);
        searchFrame.toFront();
    }

    private void openReader(Document document, String query) {
        ReaderFrame frame = new ReaderFrame(reader, exporter, document, query);
        openedReaders = (openedReaders % 8) + 1;
        frame.setLocation(cascade(openedReaders + 1));
        frame.open();
    }

    private Point cascade(int step) {
        Point origin = getLocation();
        return new Point(origin.x + step * CASCADE_STEP, origin.y + step * CASCADE_STEP);
    }

    private void beginTask(String message) {
        runningTasks++;
        status.setText(message);
        busy.setVisible(true);
    }

    private void endTask() {
        runningTasks = Math.max(0, runningTasks - 1);
        busy.setVisible(runningTasks > 0);
    }
}
