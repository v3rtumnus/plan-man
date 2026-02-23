package at.v3rtumnus.planman.controller.ui;

import at.v3rtumnus.planman.mcp.McpDiscoveryService;
import at.v3rtumnus.planman.service.PlanManUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AssistantUiController.class)
class AssistantUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private McpDiscoveryService mcpDiscoveryService;

    @MockitoBean
    private PlanManUserDetailsService userDetailsService;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void assistant_returns200AndPopulatesModel() throws Exception {
        when(mcpDiscoveryService.getAllTools()).thenReturn(Map.of());

        mockMvc.perform(get("/assistant"))
                .andExpect(status().isOk())
                .andExpect(view().name("assistant/chat"))
                .andExpect(model().attribute("serverCount", 0))
                .andExpect(model().attribute("totalTools", 0));
    }
}
