package com.github.aaronbittel.parser;

import java.util.List;

public record StmtUpdate(
    String tableName,
    List<NamedCell> keys,
    List<NamedCell> values)
    implements Stmt
{

    public StmtUpdate {
        keys = List.copyOf(keys);
        values = List.copyOf(values);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb
            .append("update ")
            .append(tableName)
            .append(" set ");

        for (int i = 0; i < values.size(); ++i) {
            if (i != 0) sb.append(" and ");
            sb.append(values.get(i));
        }

        sb.append(" where ");

        for (int i = 0; i < keys.size(); ++i) {
            if (i != 0) sb.append(" and ");
            sb.append(keys.get(i));
        }

        sb.append(";");

        return sb.toString();
    }
}
