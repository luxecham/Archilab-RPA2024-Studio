import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;

/**
 * Cree un fichier Access .mdb de test a partir d'un protocole texte :
 *   T&lt;TAB&gt;NomTable
 *   H&lt;TAB&gt;col1&lt;TAB&gt;col2...
 *   R&lt;TAB&gt;v1&lt;TAB&gt;v2...
 * Le programme est utilise uniquement pour fabriquer la fixture de test.
 */
public final class MakeAccessFixture {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: MakeAccessFixture <protocole.txt> <sortie.mdb>");
            System.exit(2);
        }
        File output = new File(args[1]);
        if (output.exists() && !output.delete()) {
            throw new IllegalStateException("Impossible de supprimer " + output);
        }
        try (Database database = DatabaseBuilder.create(Database.FileFormat.V2003, output);
             BufferedReader reader = new BufferedReader(
                 new FileReader(args[0], StandardCharsets.UTF_8))) {
            String currentName = null;
            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    flush(database, currentName, headers, rows);
                    currentName = null;
                    headers = new ArrayList<>();
                    rows = new ArrayList<>();
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts[0].equals("T")) {
                    currentName = parts[1];
                } else if (parts[0].equals("H")) {
                    for (int i = 1; i < parts.length; i++) {
                        headers.add(parts[i]);
                    }
                } else if (parts[0].equals("R")) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        row.add(parts[i]);
                    }
                    rows.add(row);
                }
            }
            flush(database, currentName, headers, rows);
        }
        System.out.println("Access fixture ecrite : " + output.getAbsolutePath());
    }

    private static void flush(Database database, String name, List<String> headers,
                              List<List<String>> rows) throws Exception {
        if (name == null || headers.isEmpty() || rows.isEmpty()) {
            return;
        }
        TableBuilder builder = new TableBuilder(name);
        for (int index = 0; index < headers.size(); index++) {
            final int columnIndex = index;
            String header = headers.get(index);
            boolean numeric = rows.stream()
                .map(row -> columnIndex < row.size() ? row.get(columnIndex).trim() : "")
                .filter(value -> !value.isEmpty())
                .allMatch(MakeAccessFixture::isNumeric);
            ColumnBuilder column = new ColumnBuilder(header);
            if (numeric) {
                column.setType(DataType.DOUBLE);
            } else {
                column.setType(DataType.TEXT).setMaxLength();
            }
            builder.addColumn(column.toColumn());
        }
        Table table = builder.toTable(database);
        for (List<String> row : rows) {
            Object[] values = new Object[headers.size()];
            for (int index = 0; index < headers.size(); index++) {
                String value = index < row.size() ? row.get(index) : "";
                if (table.getColumn(headers.get(index)).getType() == DataType.DOUBLE) {
                    values[index] = value.isEmpty() ? null : Double.valueOf(value);
                } else {
                    values[index] = value;
                }
            }
            table.addRow(values);
        }
        database.flush();
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value.replace(',', '.'));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
