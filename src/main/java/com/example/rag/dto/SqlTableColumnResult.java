package com.example.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class SqlTableColumnResult {

    private String tableName;
    private List<String> columns = new ArrayList<>();

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }
}
