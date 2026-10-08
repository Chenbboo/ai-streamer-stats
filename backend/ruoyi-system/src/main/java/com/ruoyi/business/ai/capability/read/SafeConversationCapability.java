package com.ruoyi.business.ai.capability.read;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;

/** Explicit non-business exit used when required tool selection is enabled. */
@Component
public class SafeConversationCapability implements AiCapability
{
    @Override public String code() { return "conversation.safe.respond"; }
    @Override public String description()
    {
        return "仅当用户是在打招呼、询问AI能做什么、请求超出系统范围，或确实没有表达任何可执行/可查询的业务目标时使用。"
            + "只要用户想创建、修改、审批、查询任何业务数据，就不得使用本工具，必须选择对应业务工具。";
    }
    @Override public String requiredPermission() { return "business:boss:view"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        Map<String, Object> type = AiSchemas.property(schema, "responseType", "string",
            "GREETING 表示打招呼；CAPABILITY_HELP 表示询问AI能力；OUT_OF_SCOPE 表示明确超出本系统范围；UNCLEAR 表示没有可识别目标");
        type.put("enum", Arrays.asList("GREETING", "CAPABILITY_HELP", "OUT_OF_SCOPE", "UNCLEAR"));
        return AiSchemas.required(schema, "responseType");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        String type = AiCapabilityInputs.upper(input.get("responseType"));
        String content;
        if ("GREETING".equals(type))
            content = "你好，我是老板 AI 助理。你可以直接告诉我想查询什么经营情况，或者想让我办理什么业务。";
        else if ("CAPABILITY_HELP".equals(type))
            content = "我可以按你当前账号的权限查询经营、项目和人员资料，也可以帮你准备立项、项目调整、人员管理和收支操作；写操作会先给你确认单，确认后才执行。";
        else if ("OUT_OF_SCOPE".equals(type))
            content = "这件事目前不在公司管理系统已开放的能力范围内。我不会假装已经处理；你可以换一个系统内的经营、项目、人员或收支事项。";
        else
            content = "我还没能确定你希望查询还是办理什么。请直接说目标，例如“建立一个新项目”或“查看今天经营情况”。";
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("responseType", type); result.put("_terminal", true); result.put("_content", content);
        return result;
    }
}
