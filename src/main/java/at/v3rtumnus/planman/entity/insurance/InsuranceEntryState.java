package at.v3rtumnus.planman.entity.insurance;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InsuranceEntryState {
    RECORDED(1, "eingetragen"),
    WAITING_FOR_HEALTH_INSURANCE(2, "Warte auf ÖGK"),
    HEALH_INSURANCE_RECEIVED(3, "ÖGK erhalten"),
    WAITING_FOR_PRIVATE_INSURANCE(4, "Warte auf Uniqa"),
    DONE(5, "erledigt");

    private final Integer order;
    private final String label;

    public static InsuranceEntryState fromString(String name) {
        for (InsuranceEntryState state : InsuranceEntryState.values()) {
            if (name.equals(state.name())) {
                return state;
            }
        }
        throw new RuntimeException("No respective insurance entry state found");
    }
}
