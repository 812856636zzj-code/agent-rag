package com.example.rag.service;

import com.example.rag.dto.AgentToolInput;
import com.example.rag.dto.AgentToolResult;
import com.example.rag.enums.ToolType;

public interface AgentTool {

    ToolType type();

    AgentToolResult execute(AgentToolInput input);
}
