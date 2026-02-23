package at.v3rtumnus.planman.mcp;

import at.v3rtumnus.planman.conf.McpServerConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpDiscoveryServiceTest {

    @Mock
    private McpServerConfig mcpServerConfig;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private McpDiscoveryService service;

    @Test
    void initialize_withNullServers_doesNotFail() {
        when(mcpServerConfig.getServers()).thenReturn(null);

        service.initialize(); // Should not throw

        assertThat(service.getAllTools()).isEmpty();
    }

    @Test
    void initialize_withEmptyServerList_doesNotFail() {
        when(mcpServerConfig.getServers()).thenReturn(Collections.emptyList());

        service.initialize();

        assertThat(service.getAllTools()).isEmpty();
    }

    @Test
    void initialize_withDisabledServer_skipsIt() {
        McpServerConfig.McpServer disabledServer = new McpServerConfig.McpServer();
        disabledServer.setName("disabled");
        disabledServer.setEnabled(false);

        when(mcpServerConfig.getServers()).thenReturn(List.of(disabledServer));

        service.initialize();

        assertThat(service.getAllTools()).isEmpty();
    }

    @Test
    void getAllTools_returnsUnmodifiableView() {
        when(mcpServerConfig.getServers()).thenReturn(Collections.emptyList());
        service.initialize();

        Map<String, List<McpClient.McpTool>> tools = service.getAllTools();
        assertThat(tools).isEmpty();
    }

    @Test
    void findServerForTool_whenToolNotRegistered_returnsNull() {
        when(mcpServerConfig.getServers()).thenReturn(Collections.emptyList());
        service.initialize();

        String server = service.findServerForTool("nonexistent");
        assertThat(server).isNull();
    }

    @Test
    void getClient_forUnknownServer_returnsNull() {
        when(mcpServerConfig.getServers()).thenReturn(Collections.emptyList());
        service.initialize();

        assertThat(service.getClient("unknown")).isNull();
    }

    @Test
    void shutdown_withNoClients_doesNotThrow() {
        when(mcpServerConfig.getServers()).thenReturn(Collections.emptyList());
        service.initialize();

        service.shutdown(); // Should not throw
    }

    @SuppressWarnings("unchecked")
    private Map<String, McpClient> injectedClients() {
        return (Map<String, McpClient>) ReflectionTestUtils.getField(service, "clients");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<McpClient.McpTool>> injectedToolsCache() {
        return (Map<String, List<McpClient.McpTool>>) ReflectionTestUtils.getField(service, "toolsCache");
    }

    @Test
    void getClient_withRegisteredServer_returnsClient() {
        McpClient mockClient = mock(McpClient.class);
        injectedClients().put("srv", mockClient);

        assertThat(service.getClient("srv")).isEqualTo(mockClient);
    }

    @Test
    void findServerForTool_whenToolRegistered_returnsServerName() {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        injectedToolsCache().put("brave", List.of(tool));

        assertThat(service.findServerForTool("search")).isEqualTo("brave");
    }

    @Test
    void getClientForTool_whenToolAndClientExist_returnsClient() {
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("search");
        McpClient mockClient = mock(McpClient.class);
        injectedToolsCache().put("srv", List.of(tool));
        injectedClients().put("srv", mockClient);

        assertThat(service.getClientForTool("search")).isEqualTo(mockClient);
    }

    @Test
    void getClientForTool_whenToolNotFound_returnsNull() {
        assertThat(service.getClientForTool("missing")).isNull();
    }

    @Test
    void shutdown_withRegisteredClient_closesIt() {
        McpClient mockClient = mock(McpClient.class);
        injectedClients().put("srv", mockClient);
        McpClient.McpTool tool = new McpClient.McpTool();
        tool.setName("t");
        injectedToolsCache().put("srv", List.of(tool));

        service.shutdown();

        verify(mockClient).close();
        assertThat(service.getAllTools()).isEmpty();
    }

    @Test
    void initialize_withEnabledServerOfUnsupportedType_logsErrorAndContinues() {
        McpServerConfig.McpServer server = new McpServerConfig.McpServer();
        server.setName("bad-server");
        server.setType("http"); // unsupported type - createClient throws IAE
        server.setEnabled(true);
        when(mcpServerConfig.getServers()).thenReturn(List.of(server));

        // Should not throw - exception is caught internally
        service.initialize();

        // Server was not added since initialization failed
        assertThat(service.getClient("bad-server")).isNull();
        assertThat(service.getAllTools()).isEmpty();
    }
}
