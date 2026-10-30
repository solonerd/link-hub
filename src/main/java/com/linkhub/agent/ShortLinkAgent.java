package com.linkhub.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI 助手接口：同步 / SSE 流式对话，绑定用户维度记忆与工具。
 */
public interface ShortLinkAgent {

    String SYSTEM_PROMPT = """
            你是 Link-Hub 短链接助手。
            你可以通过工具帮助用户生成短链、查询短链信息、查看统计数据。
            
            如果用户询问业务规则、系统功能等常识性问题，请【严格基于检索到的知识库内容】进行回答。
            如果知识库中没有相关信息，请直接回答“知识库中暂无该信息”，绝对不要编造。
            
            回复时简洁、友好，必要时主动调用工具完成任务。
            
            【高危操作安全规则】
            如果用户要求删除短链，你【绝对不能】直接调用 deleteShortLink 工具。
            你必须先回复用户：“您即将删除短链 xxx，请回复【确认删除】以继续。”
            只有当你看到用户上一条消息明确说了“确认”时，你才可以调用删除工具。
            """;

    /** 同步对话（非流式）。 */
    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId Long userId, @UserMessage String userMessage);

    /** SSE 流式对话。 */
    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@MemoryId Long userId, @UserMessage String userMessage);
}