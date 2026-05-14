package com.github.aaronbittel.parser;

import java.util.List;

public record StmtDelete(String tableName, List<NamedCell> keys) {

    public StmtDelete {
        keys = List.copyOf(keys);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb
            .append("delete from ")
            .append(tableName)
            .append(" where ");

        for (int i = 0; i < keys.size(); ++i) {
            if (i != 0) sb.append(" and ");
            sb.append(keys.get(i));
        }

        sb.append(";");

        return sb.toString();
    }
}
