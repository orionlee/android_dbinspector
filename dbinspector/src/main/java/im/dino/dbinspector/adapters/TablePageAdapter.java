package im.dino.dbinspector.adapters;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import im.dino.dbinspector.R;
import im.dino.dbinspector.helpers.CursorOperation;
import im.dino.dbinspector.helpers.DatabaseHelper;
import im.dino.dbinspector.helpers.PragmaType;

/**
 * Created by dino on 27/02/14.
 */
public class TablePageAdapter {

    public static final int DEFAULT_ROWS_PER_PAGE = 10;

    public interface OnClickColumnHeaderListener {
        void onClick(String columnName);
    }

    public enum SortOrder {
        ASC, DESC;
        public SortOrder toggle() {
            return (DESC == this) ? ASC : DESC;
        }
    }

    private static final SortOrder DEFAULT_SORT_ORDER = SortOrder.ASC;

    private final Context context;

    private final File databaseFile;

    private final String tableName;

    @NonNull
    private String orderByColumnName = "";
    private SortOrder orderBySortOrder = DEFAULT_SORT_ORDER;

    private int rowsPerPage = DEFAULT_ROWS_PER_PAGE;

    private int position = 0;

    private int count = 0;

    private int paddingPx;

    private String pragma;

    private OnClickColumnHeaderListener onClickColumnHeaderListener = null;

    public TablePageAdapter(Context context, File databaseFile, String tableName, int startPage) {

        this.context = context;
        this.databaseFile = databaseFile;
        this.tableName = tableName;
        paddingPx = context.getResources().getDimensionPixelSize(R.dimen.dbinspector_row_padding);

        String keyRowsPerPage = this.context.getString(R.string.dbinspector_pref_key_rows_per_page);
        String defaultRowsPerPage = this.context.getString(R.string.dbinspector_rows_per_page_default);
        String rowsPerPage = PreferenceManager.getDefaultSharedPreferences(this.context)
                .getString(keyRowsPerPage, defaultRowsPerPage);
        this.rowsPerPage = Integer.parseInt(rowsPerPage);

        int pageCount = getPageCount();
        if (startPage > pageCount) {
            startPage = pageCount;
        }
        position = this.rowsPerPage * startPage;
    }

    public List<TableRow> getByPragma(PragmaType pragmaType) {
        switch (pragmaType) {
            case FOREIGN_KEY:
                pragma = String.format(DatabaseHelper.PRAGMA_FORMAT_FOREIGN_KEYS, tableName);
                break;
            case INDEX_LIST:
                pragma = String.format(DatabaseHelper.PRAGMA_FORMAT_INDEX, tableName);
                break;
            case TABLE_INFO:
                pragma = String.format(DatabaseHelper.PRAGMA_FORMAT_TABLE_INFO, tableName);
                break;
            default:
                Log.w(DatabaseHelper.LOGTAG, "Pragma type unknown: " + pragmaType);
        }

        CursorOperation<List<TableRow>> operation = new CursorOperation<List<TableRow>>(databaseFile) {
            @Override
            public Cursor provideCursor(SQLiteDatabase database) {
                return database.rawQuery(pragma, null);
            }

            @Override
            public List<TableRow> provideResult(SQLiteDatabase database, Cursor cursor) {
                cursor.moveToFirst();
                return getTableRows(cursor, true);
            }
        };

        return operation.execute();
    }

    public List<TableRow> getContentPage() {

        CursorOperation<List<TableRow>> operation = new CursorOperation<List<TableRow>>(databaseFile) {
            @Override
            public Cursor provideCursor(SQLiteDatabase database) {
                String orderBy = TextUtils.isEmpty(orderByColumnName) ? null : orderByColumnName + " " + orderBySortOrder.name();
                return database.query(tableName, null, null, null, null, null, orderBy);
            }

            @Override
            public List<TableRow> provideResult(SQLiteDatabase database, Cursor cursor) {
                count = cursor.getCount();
                cursor.moveToPosition(position);
                return getTableRows(cursor, false);
            }
        };

        return operation.execute();
    }

    private List<TableRow> getTableRows(Cursor cursor, boolean allRows) {

        List<TableRow> rows = new ArrayList<>();
        TableRow header = new TableRow(context);

        for (int col = 0; col < cursor.getColumnCount(); col++) {
            final String columnName = cursor.getColumnName(col);
            TextView textView = new TextView(context);
            if (columnName.equals(orderByColumnName)) {
                // prepend up/down arrows (unicodes ↑ ↓) to show sort order
                // (make it prepend rather than append in case the column width is shortened so that it gets truncated)
                String displayText = (orderBySortOrder == SortOrder.ASC ? "\u2191 " : "\u2193 ")
                        + columnName;
                textView.setText(displayText);
                textView.setTextColor(context.getResources().getColor(R.color.dbinspector_sort_column_header_foreground));
            } else {
                textView.setText(columnName);
            }
            textView.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onClickColumnHeaderListener != null) {
                        onClickColumnHeaderListener.onClick(columnName);
                    }
                }
            });
            header.addView(textView);
        }

        rows.add(header);

        boolean alternate = true;

        if (cursor.getCount() == 0) {
            return rows;
        }

        do {
            TableRow row = new TableRow(context);

            for (int col = 0; col < cursor.getColumnCount(); col++) {
                TextView textView = new TextView(context);
                if (DatabaseHelper.getColumnType(cursor, col) == DatabaseHelper.FIELD_TYPE_BLOB) {
                    textView.setText("(data)");
                } else {
                    textView.setText(cursor.getString(col));
                }
                textView.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2);

                if (alternate) {
                    textView.setBackgroundColor(context.getResources().getColor(R.color.dbinspector_alternate_row_background));
                }

                row.addView(textView);
            }

            alternate = !alternate;
            rows.add(row);

        } while (cursor.moveToNext() && (allRows || rows.size() <= rowsPerPage));

        return rows;
    }

    public void nextPage() {
        if (position + rowsPerPage < count) {
            position += rowsPerPage;
        }
    }

    public void previousPage() {
        if (position - rowsPerPage >= 0) {
            position -= rowsPerPage;
        }
    }

    public boolean hasNext() {
        return position + rowsPerPage < count;
    }

    public boolean hasPrevious() {
        return position - rowsPerPage >= 0;
    }

    public int getPageCount() {
        return (int) Math.ceil((float) count / rowsPerPage);
    }

    public int getCurrentPage() {
        return position / rowsPerPage + 1;
    }

    /**
     * Go back to the first page.
     */
    public void resetPage() {
        position = 0;
    }

    public OnClickColumnHeaderListener getOnClickColumnHeaderListener() {
        return onClickColumnHeaderListener;
    }

    public void setOnClickColumnHeaderListener(OnClickColumnHeaderListener onClickColumnHeaderListener) {
        this.onClickColumnHeaderListener = onClickColumnHeaderListener;
    }

    @NonNull
    public String getOrderByColumnName() {
        return orderByColumnName;
    }

    public SortOrder getOrderBySortOrder() {
        return orderBySortOrder;
    }

    public void toggleOrderByColumn(@NonNull String orderByColumnName) {

        if (orderByColumnName.equals(this.orderByColumnName)) {
            // case toggle order of the currently sorted column
            this.orderBySortOrder = this.orderBySortOrder.toggle();
        } else { // case specify a new sort column (or reset to null)
            this.orderByColumnName = orderByColumnName;
            this.orderBySortOrder = DEFAULT_SORT_ORDER; // reset sort order to defaults too
        }
        resetPage(); // reset paging as well once order by is changed.
    }


}
