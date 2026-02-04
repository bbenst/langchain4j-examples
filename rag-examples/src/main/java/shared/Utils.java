package shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Scanner;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * 通用工具类，提供环境变量读取、资源路径与交互式对话等能力。
 */
public class Utils {

    /**
     * OpenAI API Key（部分示例仍可能依赖该值）。
     */
    public static final String OPENAI_API_KEY = getOrDefault(System.getenv("OPENAI_API_KEY"), "demo");

    /**
     * 阿里云百炼 DashScope API Key，用于 Qwen 模型调用。
     */
    public static final String DASHSCOPE_API_KEY = getOrDefault(System.getenv("DASHSCOPE_API_KEY"), "sk-3766c418f3c441b4bfd284b7b3f3fb00");

    /**
     * 阿里云百炼聊天模型名称，默认使用 qwen-plus。
     */
    public static final String DASHSCOPE_CHAT_MODEL = getOrDefault(System.getenv("DASHSCOPE_CHAT_MODEL"), "qwen-plus");

    /**
     * 启动一个基于控制台输入的对话循环。
     *
     * @param assistant 用于回答用户问题的 AI 助手
     */
    public static void startConversationWith(Assistant assistant) {
        Logger log = LoggerFactory.getLogger(Assistant.class);
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                log.info("==================================================");
                log.info("User: ");
                String userQuery = scanner.nextLine();
                log.info("==================================================");

                if ("exit".equalsIgnoreCase(userQuery)) {
                    break;
                }

                String agentAnswer = assistant.answer(userQuery);
                log.info("==================================================");
                log.info("Assistant: " + agentAnswer);
            }
        }
    }

    /**
     * 生成用于文件匹配的 Glob 规则。
     *
     * @param glob Glob 表达式
     * @return PathMatcher 实例
     */
    public static PathMatcher glob(String glob) {
        return FileSystems.getDefault().getPathMatcher("glob:" + glob);
    }

    /**
     * 将类路径下的资源转为可访问的路径。
     *
     * @param relativePath 资源的相对路径
     * @return 资源对应的 Path
     * @throws RuntimeException 当资源无法转换为 URI 时抛出
     */
    public static Path toPath(String relativePath) {
        try {
            URL fileUrl = Utils.class.getClassLoader().getResource(relativePath);
            return Paths.get(fileUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
