package views;

import javax.swing.table.DefaultTableCellRenderer;

import models.Snippet;

/** Renders a snippet with the matched term highlighted. */
final class SnippetRenderer extends DefaultTableCellRenderer {

    private static final String MATCH_STYLE = "background:#FFD84D;font-weight:bold";

    @Override
    protected void setValue(Object value) {
        if (value instanceof Snippet snippet) {
            setText("<html><nobr>" + escape(snippet.before())
                    + "<span style='" + MATCH_STYLE + "'>" + escape(snippet.matched()) + "</span>"
                    + escape(snippet.after()) + "</nobr></html>");
        } else {
            super.setValue(value);
        }
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
