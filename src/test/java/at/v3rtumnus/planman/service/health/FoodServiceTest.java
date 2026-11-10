package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.FoodRepository;
import at.v3rtumnus.planman.dto.health.MealItemForm;
import at.v3rtumnus.planman.entity.health.Food;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FoodServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Vienna");
    private final Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 10, 12, 0).atZone(ZONE).toInstant(), ZONE);
    private final FoodRepository foods = mock(FoodRepository.class);
    private final HttpClient http = mock(HttpClient.class);
    private final FoodService service = new FoodService(foods, http, clock, "https://off.test");

    private static final String SKYR = """
            {"status":1,"product":{"product_name":"Skyr natur","product_name_de":"Skyr Natur","brands":"Milbona, Lidl",
             "serving_quantity":"150","serving_size":"150 g",
             "nutriments":{"energy-kcal_100g":63,"proteins_100g":11,"carbohydrates_100g":4.1,"fat_100g":0.2}}}""";

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.<String>send(any(HttpRequest.class), any())).thenReturn(response);
    }

    @Test
    void lookup_fetchesFromOpenFoodFactsAndCaches() throws Exception {
        when(foods.findById("4056489123456")).thenReturn(Optional.empty());
        when(foods.save(any(Food.class))).thenAnswer(i -> i.getArgument(0));
        respond(200, SKYR);

        Food food = service.lookup("4056489123456").orElseThrow();

        assertThat(food.getName()).isEqualTo("Skyr Natur");
        assertThat(food.getBrand()).isEqualTo("Milbona");
        assertThat(food.getKcalPer100g()).isEqualByComparingTo("63");
        assertThat(food.getProteinPer100g()).isEqualByComparingTo("11");
        assertThat(food.getCarbsPer100g()).isEqualByComparingTo("4.1");
        assertThat(food.getFatPer100g()).isEqualByComparingTo("0.2");
        assertThat(food.getServingG()).isEqualTo(150);
        assertThat(food.getSource()).isEqualTo(Food.Source.OFF);
        verify(foods).save(food);

        MealItemForm item = FoodService.item(food);
        assertThat(item.getName()).isEqualTo("Skyr Natur (Milbona)");
        assertThat(item.getGrams()).isEqualTo(150);
        assertThat(item.getPortionHint()).isEqualTo("Portion laut Packung: 150 g");
    }

    @Test
    void lookup_usesTheLocalRowFirst() {
        Food known = new Food();
        known.setName("Mein Müsli");
        when(foods.findById("12345678")).thenReturn(Optional.of(known));

        assertThat(service.lookup("12345678")).contains(known);
        verifyNoInteractions(http);
    }

    @Test
    void lookup_unknownOrBrokenOrInvalid() throws Exception {
        when(foods.findById(any())).thenReturn(Optional.empty());
        respond(404, "");
        assertThat(service.lookup("12345678")).isEmpty();

        respond(200, "{\"status\":0}");
        assertThat(service.lookup("12345678")).isEmpty();

        respond(503, "");
        assertThatThrownBy(() -> service.lookup("12345678")).isInstanceOf(FoodService.LookupException.class)
                .hasMessageContaining("HTTP 503");

        when(http.<String>send(any(HttpRequest.class), any())).thenThrow(new IOException("down"));
        assertThatThrownBy(() -> service.lookup("12345678")).isInstanceOf(FoodService.LookupException.class);

        assertThatThrownBy(() -> service.lookup("12ab")).isInstanceOf(IllegalArgumentException.class);
        assertThat(FoodService.validBarcode(null)).isFalse();
    }

    @Test
    void parse_kilojouleFallbackAndMissingValues() {
        Food food = service.parse("12345678", """
                {"status":1,"product":{"product_name":"Brot","nutriments":{"energy_100g":"1046","fat_100g":-1}}}""").orElseThrow();

        assertThat(food.getKcalPer100g()).isEqualByComparingTo("250.0");
        assertThat(food.getProteinPer100g()).isEqualByComparingTo("0");
        assertThat(food.getFatPer100g()).isEqualByComparingTo("0");
        assertThat(food.getBrand()).isNull();
        assertThat(food.getServingG()).isNull();
        assertThat(FoodService.item(food).getGrams()).isEqualTo(100);
        assertThat(FoodService.item(food).getPortionHint()).isEqualTo("Menge anpassen");

        assertThat(service.parse("1", "{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{}}}")).isEmpty();
        assertThat(service.parse("1", "{\"status\":1,\"product\":{\"nutriments\":{\"energy-kcal_100g\":5}}}")).isEmpty();
        assertThat(service.parse("1", "{\"status\":1,\"product\":{\"product_name\":\"Y\",\"nutriments\":{\"energy-kj_100g\":\"x\",\"energy-kcal_100g\":\"abc\"}}}")).isEmpty();
        assertThat(service.parse("1", "{\"status\":1,\"product\":{\"product_name\":\"Z\",\"nutriments\":{\"energy-kj_100g\":418.4}}}"))
                .get().extracting(Food::getKcalPer100g).satisfies(k -> assertThat(k).isEqualByComparingTo("100"));
    }

    @Test
    void remember_savesYourCorrectionOnlyWhenChanged() {
        Food food = new Food();
        food.setBarcode("12345678");
        food.setName("Skyr");
        food.setKcalPer100g(new BigDecimal("63"));
        food.setProteinPer100g(new BigDecimal("11"));
        food.setCarbsPer100g(new BigDecimal("4"));
        food.setFatPer100g(new BigDecimal("0.2"));
        when(foods.findById("12345678")).thenReturn(Optional.of(food));
        MealItemForm same = new MealItemForm("Skyr", 150, new BigDecimal("63.0"), new BigDecimal("11"), new BigDecimal("4"),
                new BigDecimal("0.20"), false, null);

        service.remember("12345678", same);
        verify(foods, never()).save(any());

        same.setKcalPer100g(new BigDecimal("70"));
        service.remember("12345678", same);
        assertThat(food.getKcalPer100g()).isEqualByComparingTo("70");
        assertThat(food.getSource()).isEqualTo(Food.Source.USER);
        verify(foods).save(food);

        when(foods.findById("87654321")).thenReturn(Optional.empty());
        service.remember("87654321", same);
        verify(foods).save(argThat(f -> "87654321".equals(f.getBarcode()) && "Skyr".equals(f.getName())));

        service.remember("bad", same);
        service.remember("12345678", null);
        verify(foods, times(2)).save(any());
    }
}
