package app;

import java.nio.file.Path;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import controllers.ExportController;
import controllers.LibraryController;
import controllers.ReaderController;
import controllers.SearchController;
import models.CaseInsensitivePhraseStrategy;
import models.DocumentRepository;
import models.FileDocumentRepository;
import models.MatchStrategy;
import models.SearchIndex;
import models.StorageException;
import views.MainFrame;

/**
 * Entry point and composition root: the only place that knows which concrete
 * classes are wired together.
 *
 * <p>The library lives in {@code ~/.localsearch} unless the system property
 * {@code localsearch.home} points elsewhere.
 */
public final class Main {

    private static final String HOME_PROPERTY = "localsearch.home";
    private static final String DEFAULT_HOME = ".localsearch";

    private Main() {
    }

    public static void main(String[] args) {
        Path home = Path.of(System.getProperty(HOME_PROPERTY,
                Path.of(System.getProperty("user.home"), DEFAULT_HOME).toString()));

        DocumentRepository repository;
        try {
            repository = new FileDocumentRepository(home);
        } catch (StorageException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot open library", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        SearchIndex index = new SearchIndex();
        MatchStrategy strategy = new CaseInsensitivePhraseStrategy();
        LibraryController library = new LibraryController(repository, index);
        SearchController search = new SearchController(index, strategy);
        ReaderController reader = new ReaderController(repository, index, strategy);
        ExportController exporter = new ExportController();

        SwingUtilities.invokeLater(() -> {
            useSystemLookAndFeel();
            MainFrame frame = new MainFrame(library, search, reader, exporter);
            frame.setVisible(true);
            frame.loadLibrary();
        });
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // the cross-platform default look and feel is fine
        }
    }
}
