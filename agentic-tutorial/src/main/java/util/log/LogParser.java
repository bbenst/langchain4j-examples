package util.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static String truncateString(String input) {
        int maxChars = CustomLogging.getCharLimit();
        if (input == null || input.length() <= maxChars) {
            return input;
        }
        
        int firstHalf = maxChars / 2;
        int secondHalf = maxChars / 2;
        
        return input.substring(0, firstHalf) + "\n[... truncated ...]\n" + 
               input.substring(input.length() - secondHalf);
    }
    
    public static void logUserMessage(String userMessage) {
        System.out.println("USER: " + truncateString(userMessage));
        System.out.println(); // 2 newlines for clear separation
        // 两个换行用于清晰分隔
        System.out.println();
    }
    
    public static void logAssistantResponse(String response) {
        System.out.println("MODEL: " + truncateString(response));
        System.out.println(); // 2 newlines for clear separation
        // 两个换行用于清晰分隔
        System.out.println();
    }
    
    public static void logAvailableTools(String tools) {
        System.out.println("\tAvailable tools: " + tools);
        System.out.println(); // 2 newlines for clear separation
        // 两个换行用于清晰分隔
        System.out.println();
    }
    
    public static void logToolCallRequest(String toolId, String toolName, String arguments) {
        System.out.println("MODEL REQUESTS TOOL CALL: " + toolName + " (id: " + toolId + ")");
        System.out.println("  Args: " + truncateString(arguments));
        System.out.println(); // 2 newlines for clear separation
        // 两个换行用于清晰分隔
        System.out.println();
    }
    

    public static void logToolCallResult(String toolId, String toolName, String result) {
        System.out.println("TOOL RESULT: " + toolName + " (id: " + toolId + ")");
        System.out.println("  Result: " + truncateString(result));
        System.out.println(); // 2 newlines for clear separation
        // 两个换行用于清晰分隔
        System.out.println();
    }
    
    public static void parseHttpRequest(String logMessage) {
        if (!logMessage.contains("HTTP request:") || !logMessage.contains("- body:")) {
            return;
        }
        
        try {
            String jsonBody = extractJsonFromLog(logMessage);
            if (jsonBody == null) return;
            
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode messages = root.get("messages");
            JsonNode tools = root.get("tools");
            
            if (messages == null || !messages.isArray()) return;
            
            // Find the LAST message in the conversation (what's new)
            // 查找对话中最后一条消息（最新内容）
            JsonNode lastMessage = messages.get(messages.size() - 1);
            if (lastMessage == null) return;
            
            String role = lastMessage.get("role").asText();
            
            if ("user".equals(role)) {
                // New user question
                // 新的用户问题
                String content = lastMessage.get("content").asText();
                if (content != null && !content.isEmpty()) {
                    logUserMessage(content);
                }
                
                // Show available tools AFTER user message when tools are present
                // 当存在工具时，在用户消息后显示可用工具
                if (tools != null && tools.isArray() && tools.size() > 0) {
                    StringBuilder toolNames = new StringBuilder();
                    for (JsonNode tool : tools) {
                        if (toolNames.length() > 0) toolNames.append(", ");
                        toolNames.append(tool.get("function").get("name").asText());
                    }
                    logAvailableTools(toolNames.toString());
                }
            } else if ("tool".equals(role)) {
                // New tool result
                // 新的工具结果
                String toolCallId = lastMessage.get("tool_call_id").asText();
                String content = lastMessage.get("content").asText();
                String toolName = extractToolNameFromHistory(messages, toolCallId);
                logToolCallResult(toolCallId, toolName, content);
            } else if ("assistant".equals(role)) {
                // Check if this is a final response (not a tool call)
                // 检查是否为最终响应（非工具调用）
                JsonNode toolCalls = lastMessage.get("tool_calls");
                if (toolCalls == null || !toolCalls.isArray() || toolCalls.size() == 0) {
                    String content = lastMessage.get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        logAssistantResponse(content);
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore parsing errors
            // 忽略解析错误
        }
    }
    

    private static String extractToolNameFromHistory(JsonNode messages, String toolCallId) {
        for (JsonNode message : messages) {
            String role = message.get("role").asText();
            if ("assistant".equals(role)) {
                JsonNode toolCalls = message.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray()) {
                    for (JsonNode toolCall : toolCalls) {
                        String id = toolCall.get("id").asText();
                        if (toolCallId.equals(id)) {
                            return toolCall.get("function").get("name").asText();
                        }
                    }
                }
            }
        }
        return "unknown";
    }

    public static void parseHttpResponse(String logMessage) {
        if (!logMessage.contains("HTTP response:") || !logMessage.contains("- body:")) {
            return;
        }
        
        try {
            String jsonBody = extractJsonFromLog(logMessage);
            if (jsonBody == null) return;
            
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode choices = root.get("choices");
            
            if (choices == null || !choices.isArray() || choices.size() == 0) return;
            
            JsonNode message = choices.get(0).get("message");
            String content = message.get("content").asText();
            
            // Check for tool calls first
            // 先检查工具调用
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                // New tool call requests
                // 新的工具调用请求
                for (JsonNode toolCall : toolCalls) {
                    String toolId = toolCall.get("id").asText();
                    String toolName = toolCall.get("function").get("name").asText();
                    String arguments = toolCall.get("function").get("arguments").asText();
                    logToolCallRequest(toolId, toolName, arguments);
                }
            } else if (content != null && !content.isEmpty()) {
                // New assistant response (no tool calls)
                // 新的助手响应（无工具调用）
                logAssistantResponse(content);
            }
            
        } catch (Exception e) {
            // Ignore parsing errors
            // 忽略解析错误
        }
    }
    
    private static String extractJsonFromLog(String logMessage) {
        // Find the JSON body after "- body:"
        // 查找 "- body:" 之后的 JSON 内容
        Pattern pattern = Pattern.compile("- body:\\s*(.*?)(?=\\n\\n|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(logMessage);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
}
