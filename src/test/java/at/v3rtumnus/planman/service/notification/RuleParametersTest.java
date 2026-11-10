package at.v3rtumnus.planman.service.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleParametersTest {

    @Test
    void readsNumbersWithFallbacks() {
        RuleParameters params = RuleParameters.parse("{\"days\":14,\"thresholdAmount\":300.5}");

        assertThat(params.integer("days", 1)).isEqualTo(14);
        assertThat(params.decimal("thresholdAmount", BigDecimal.ONE)).isEqualByComparingTo("300.5");
        assertThat(params.integer("missing", 7)).isEqualTo(7);
        assertThat(params.decimal("missing", BigDecimal.TEN)).isEqualByComparingTo("10");
        assertThat(RuleParameters.parse(null).integer("days", 3)).isEqualTo(3);
        assertThat(RuleParameters.parse(" ").integer("days", 3)).isEqualTo(3);
    }

    @Test
    void rejectsNonNumbersAndNegatives() {
        assertThatThrownBy(() -> RuleParameters.read("{\"days\":\"viele\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleParameters.read("[1]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleParameters.read("{\"days\":-1}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleParameters.read("{\"days\":null}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesBackInOrder() {
        assertThat(RuleParameters.write(RuleParameters.read("{\"b\":2,\"a\":1}"))).isEqualTo("{\"b\":2,\"a\":1}");
        assertThat(RuleParameters.write(Map.of())).isEqualTo("{}");
    }
}
