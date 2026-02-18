package at.v3rtumnus.planman.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String response;
    private String conversationId;
    private String traceId;
    private boolean success;
    private String error;
    private long durationMs;
    private int anonymizedEntities;
    private List<String> toolsUsed;
}
