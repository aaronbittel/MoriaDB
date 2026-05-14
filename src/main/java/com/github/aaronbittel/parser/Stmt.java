package com.github.aaronbittel.parser;

public sealed interface Stmt
    permits StmtCreateTable, StmtSelect, StmtInsert, StmtUpdate, StmtDelete {}
