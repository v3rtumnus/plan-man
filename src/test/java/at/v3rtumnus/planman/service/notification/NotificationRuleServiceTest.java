package at.v3rtumnus.planman.service.notification;

import at.v3rtumnus.planman.dao.NotificationRepository;
import at.v3rtumnus.planman.dao.NotificationRuleRepository;
import at.v3rtumnus.planman.dto.notification.NotificationRuleDTO;
import at.v3rtumnus.planman.dto.notification.NotificationRuleUpdate;
import at.v3rtumnus.planman.entity.UserProfile;
import at.v3rtumnus.planman.entity.notification.NotificationRule;
import at.v3rtumnus.planman.entity.notification.NotificationTargetingMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationRuleServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 10, 12, 0).atZone(ZONE).toInstant(), ZONE);
    private final NotificationRuleRepository rules = mock(NotificationRuleRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationRuleService service = new NotificationRuleService(rules, notifications, clock);

    private static NotificationRule rule(long id, String name, String json, NotificationTargetingMode mode) {
        NotificationRule rule = new NotificationRule();
        rule.setId(id);
        rule.setName(name);
        rule.setRuleType("T" + id);
        rule.setParametersJson(json);
        rule.setTargetingMode(mode);
        return rule;
    }

    @Test
    void list_showsLabelsTargetsAndCounts() {
        NotificationRule budget = rule(4, "Budget knapp", "{\"thresholdAmount\":300}", NotificationTargetingMode.ALL_USERS);
        NotificationRule price = rule(7, "Kursbewegung", "{\"sharePercent\":20,\"custom\":1}", NotificationTargetingMode.SPECIFIC_USERS);
        UserProfile michael = new UserProfile();
        michael.setUsername("michael");
        price.getTargetUsers().add(michael);
        when(rules.findAll()).thenReturn(List.of(price, budget));
        LocalDateTime since = LocalDateTime.of(2026, 10, 11, 12, 0);
        when(notifications.countByRuleAndCreatedAtAfter(budget, since)).thenReturn(4L);
        when(notifications.countByRuleAndCreatedAtAfterAndReadAtIsNotNull(budget, since)).thenReturn(3L);

        List<NotificationRuleDTO> list = service.list();

        assertThat(list).extracting(NotificationRuleDTO::name).containsExactly("Budget knapp", "Kursbewegung");
        assertThat(list.getFirst().targets()).isEqualTo("Alle");
        assertThat(list.getFirst().fired30Days()).isEqualTo(4);
        assertThat(list.getFirst().read30Days()).isEqualTo(3);
        assertThat(list.getFirst().parameters()).singleElement()
                .satisfies(p -> assertThat(p.label()).isEqualTo("Schwelle (€)"));
        assertThat(list.get(1).targets()).isEqualTo("michael");
        assertThat(list.get(1).parameters()).extracting(NotificationRuleDTO.Parameter::label)
                .containsExactly("Aktien ± %", "custom");
    }

    @Test
    void update_changesKnownKeysAndEnabled() {
        NotificationRule budget = rule(4, "Budget knapp", "{\"thresholdAmount\":300,\"other\":1}", NotificationTargetingMode.ALL_USERS);
        when(rules.findById(4L)).thenReturn(Optional.of(budget));

        service.update(4L, new NotificationRuleUpdate(false, Map.of("thresholdAmount", new BigDecimal("250"))));

        assertThat(budget.getParametersJson()).isEqualTo("{\"thresholdAmount\":250,\"other\":1}");
        assertThat(budget.isEnabled()).isFalse();
        verify(rules).save(budget);
    }

    @Test
    void update_withoutParametersOnlyToggles() {
        NotificationRule budget = rule(4, "Budget knapp", "{\"thresholdAmount\":300}", NotificationTargetingMode.ALL_USERS);
        when(rules.findById(4L)).thenReturn(Optional.of(budget));

        service.update(4L, new NotificationRuleUpdate(true, null));

        assertThat(budget.getParametersJson()).isEqualTo("{\"thresholdAmount\":300}");
        assertThat(budget.isEnabled()).isTrue();
    }

    @Test
    void update_rejectsUnknownKeysNegativesAndMissingRules() {
        NotificationRule budget = rule(4, "Budget knapp", "{\"thresholdAmount\":300}", NotificationTargetingMode.ALL_USERS);
        when(rules.findById(4L)).thenReturn(Optional.of(budget));
        Map<String, BigDecimal> withNull = new HashMap<>();
        withNull.put("thresholdAmount", null);

        assertThatThrownBy(() -> service.update(4L, new NotificationRuleUpdate(true, Map.of("days", BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(4L, new NotificationRuleUpdate(true, Map.of("thresholdAmount", new BigDecimal("-5")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(4L, new NotificationRuleUpdate(true, withNull)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(99L, new NotificationRuleUpdate(true, Map.of())))
                .isInstanceOf(NoSuchElementException.class);
        verify(rules, never()).save(any());
    }
}
