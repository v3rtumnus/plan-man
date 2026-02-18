package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.mcp.McpClient;
import at.v3rtumnus.planman.mcp.McpDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AssistantUiController {

    private final McpDiscoveryService mcpDiscoveryService;

    @GetMapping("/assistant")
    public String assistant(Model model) {
        Map<String, List<McpClient.McpTool>> allTools = mcpDiscoveryService.getAllTools();
        model.addAttribute("mcpServers", allTools);
        model.addAttribute("serverCount", allTools.size());

        int totalTools = allTools.values().stream()
            .mapToInt(List::size)
            .sum();
        model.addAttribute("totalTools", totalTools);

        return "assistant/chat";
    }
}
