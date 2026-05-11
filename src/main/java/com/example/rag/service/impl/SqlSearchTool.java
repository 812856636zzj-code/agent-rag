package com.example.rag.service.impl;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.dto.SqlTableColumnResult;
import com.example.rag.enums.ToolType;
import com.example.rag.service.AgentTool;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlSearchTool implements AgentTool {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9_]*\\b");
    private static final Pattern SAFE_TABLE_NAME_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ToolType type() {
        return ToolType.SQL_SEARCH;
    }

    @Override
    public AgentToolResult execute(AgentToolInput input) {
        long start = System.currentTimeMillis();
        AgentToolResult result = new AgentToolResult();
        result.setToolType(type());
        try {
            String tableName = extractTableName(input == null ? null : input.getQuestion());
            if (!StringUtils.hasText(tableName) || !SAFE_TABLE_NAME_PATTERN.matcher(tableName).matches()) {
                result.setSuccess(true);
                result.setData(new SqlTableColumnResult());
                result.setSummary("sqlMetadataColumns=0");
                result.setLatencyMs(System.currentTimeMillis() - start);
                return result;
            }

            Query query = entityManager.createNativeQuery(
                    "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = :tableName ORDER BY COLUMN_ID"
            );
            query.setParameter("tableName", tableName);

            @SuppressWarnings("unchecked")
            List<Object> rows = query.getResultList();

            SqlTableColumnResult sqlResult = new SqlTableColumnResult();
            sqlResult.setTableName(tableName);
            List<String> columns = new ArrayList<>();
            for (Object row : rows) {
                if (row != null) {
                    columns.add(String.valueOf(row));
                }
            }
            sqlResult.setColumns(columns);

            result.setSuccess(true);
            result.setData(sqlResult);
            result.setSummary("sqlMetadataColumns=" + columns.size());
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException ex) {
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            result.setSummary("sql metadata search failed");
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    private String extractTableName(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String upper = question.toUpperCase(Locale.ROOT);
        Matcher matcher = TABLE_NAME_PATTERN.matcher(upper);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (SAFE_TABLE_NAME_PATTERN.matcher(candidate).matches()
                    && !isReservedWord(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean isReservedWord(String candidate) {
        return "SELECT".equals(candidate)
                || "FROM".equals(candidate)
                || "WHERE".equals(candidate)
                || "ORDER".equals(candidate)
                || "BY".equals(candidate)
                || "COLUMN_NAME".equals(candidate)
                || "USER_TAB_COLUMNS".equals(candidate);
    }
}
