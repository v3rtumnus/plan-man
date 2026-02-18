package at.v3rtumnus.planman.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "assistant.mcp")
public class McpServerConfig {

    private List<McpServer> servers;

    @Data
    public static class McpServer {
        private String name;
        private String type;
        private String command;
        private List<String> args;
        private Map<String, String> env;
        private boolean enabled = true;
        private String description;
    }
}
