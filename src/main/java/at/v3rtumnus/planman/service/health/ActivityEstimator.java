package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dto.health.ai.ActivityDraft;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** "Etwas anderes": one sentence → title and MET, so the kcal follow the same formula as the rest. */
@Service
@RequiredArgsConstructor
public class ActivityEstimator {

    static final String SYSTEM_PROMPT = """
            Du ordnest eine Freizeit- oder Sportaktivität aus einem deutschen Satz einem MET-Wert zu
            (Compendium of Physical Activities, Erwachsene). Gib einen kurzen deutschen Namen, den MET-Wert
            für die beschriebene Intensität (ohne Angabe: übliche Freizeitintensität) und die Dauer in
            Minuten, falls genannt (sonst 0). Pausen und Wartezeiten sind im MET schon enthalten, wenn die
            Aktivität sie üblicherweise hat (z. B. Tennis Doppel, Skifahren mit Lift). Gültige MET-Werte
            liegen zwischen 1,5 und 16.
            """;

    private final AnthropicGateway gateway;

    @Value("${planman.health.ai.activity-model:claude-sonnet-5-5}")
    private String model;

    public boolean available() {
        return gateway.available();
    }

    public ActivityDraft estimate(String description) {
        if (description == null || description.isBlank()) {
            throw new AiRequestException("Bitte die Aktivität in einem Satz beschreiben");
        }
        ActivityDraft draft = gateway.structured(model, "activity", SYSTEM_PROMPT,
                List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(description.trim()).build())),
                ActivityDraft.class).value();
        if (draft.met() < 1.5 || draft.met() > 16) {
            throw new AiRequestException("Die KI konnte die Aktivität nicht einschätzen. Bitte Gehen / Rad wählen oder genauer beschreiben.");
        }
        return draft;
    }
}
