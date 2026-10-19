package views;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import models.SearchResult;
import models.Snippet;

/** Search results (use case #2): title and a snippet around the first match. */
final class SearchResultTableModel extends AbstractTableModel {

    static final int TITLE = 0;
    static final int SNIPPET = 1;

    private static final String[] COLUMNS = {"Title", "First match"};

    private List<SearchResult> results = List.of();

    void setResults(List<SearchResult> newResults) {
        results = List.copyOf(newResults);
        fireTableDataChanged();
    }

    SearchResult resultAt(int row) {
        return results.get(row);
    }

    @Override
    public int getRowCount() {
        return results.size();
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
        return column == SNIPPET ? Snippet.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        SearchResult result = results.get(row);
        return column == SNIPPET ? result.snippet() : result.document().title();
    }
}
