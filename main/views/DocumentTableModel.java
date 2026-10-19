package views;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.table.AbstractTableModel;

import models.Document;

/** Main table (use case #3): title, 500-character preview, delete checkbox. */
final class DocumentTableModel extends AbstractTableModel {

    static final int TITLE = 0;
    static final int PREVIEW = 1;
    static final int DELETE = 2;

    private static final String[] COLUMNS = {"Title", "Preview (first 500 characters)", "Delete"};

    private List<Document> documents = List.of();
    private Set<String> checkedIds = Set.of();

    /** Replaces the rows; ticks survive for documents that are still present. */
    void setDocuments(List<Document> newDocuments) {
        documents = List.copyOf(newDocuments);
        Set<String> present = documents.stream().map(Document::id).collect(Collectors.toSet());
        checkedIds = checkedIds.stream().filter(present::contains).collect(Collectors.toUnmodifiableSet());
        fireTableDataChanged();
    }

    List<Document> checkedDocuments() {
        return documents.stream().filter(doc -> checkedIds.contains(doc.id())).toList();
    }

    Document documentAt(int row) {
        return documents.get(row);
    }

    @Override
    public int getRowCount() {
        return documents.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == DELETE ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == DELETE;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Document doc = documents.get(row);
        return switch (column) {
            case TITLE -> doc.title();
            case PREVIEW -> doc.preview();
            case DELETE -> checkedIds.contains(doc.id());
            default -> throw new IllegalArgumentException("column " + column);
        };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != DELETE) {
            return;
        }
        String id = documents.get(row).id();
        Set<String> next = new HashSet<>(checkedIds);
        if (Boolean.TRUE.equals(value)) {
            next.add(id);
        } else {
            next.remove(id);
        }
        checkedIds = Set.copyOf(next);
        fireTableCellUpdated(row, column);
    }
}
