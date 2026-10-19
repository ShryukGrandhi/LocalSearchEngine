package views;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import models.Document;

/**
 * Use case #3 confirmation: lists every title about to be deleted. The list
 * sits in a scroll pane so the dialog stays a usable size for any count.
 */
final class DeleteConfirmDialog {

    private static final int VISIBLE_ROWS = 10;
    private static final Dimension LIST_SIZE = new Dimension(420, 200);

    private DeleteConfirmDialog() {
    }

    static boolean confirm(Component parent, List<Document> documents) {
        JList<String> titles = new JList<>(documents.stream().map(Document::title).toArray(String[]::new));
        titles.setVisibleRowCount(Math.min(VISIBLE_ROWS, documents.size()));
        JScrollPane scroll = new JScrollPane(titles);
        scroll.setPreferredSize(LIST_SIZE);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Delete these " + documents.size() + " document(s)? This cannot be undone."),
                BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        Object[] options = {"Delete", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent, panel, "Confirm delete",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    }
}
