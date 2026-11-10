package at.v3rtumnus.planman.service.health;

import at.v3rtumnus.planman.dao.FoodRepository;
import at.v3rtumnus.planman.dto.health.MealItemForm;
import at.v3rtumnus.planman.entity.health.Food;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Packaged food by barcode (H7): the local {@code food} table first, then Open Food Facts
 * (keyless, the product is cached on the first scan). Your corrections on the review form
 * are saved back, so the next scan uses them.
 */
@Service
@Slf4j
public class FoodService {

    /** Open Food Facts unreachable or broken; the message is shown on the capture page. */
    public static class LookupException extends RuntimeException {
        public LookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final Pattern BARCODE = Pattern.compile("\\d{8,14}");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");

    private final FoodRepository foods;
    private final HttpClient http;
    private final Clock clock;
    private final String baseUrl;

    @Autowired
    public FoodService(FoodRepository foods, Clock clock,
                       @Value("${planman.health.off-url:https://world.openfoodfacts.org}") String baseUrl) {
        this(foods, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                clock, baseUrl);
    }

    FoodService(FoodRepository foods, HttpClient http, Clock clock, String baseUrl) {
        this.foods = foods;
        this.http = http;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    public static boolean validBarcode(String barcode) {
        return barcode != null && BARCODE.matcher(barcode).matches();
    }

    /** The product, or empty if the barcode is unknown here and at Open Food Facts. */
    @Transactional
    public Optional<Food> lookup(String barcode) {
        if (!validBarcode(barcode)) {
            throw new IllegalArgumentException("Kein gültiger Barcode (8 bis 14 Ziffern)");
        }
        Optional<Food> known = foods.findById(barcode);
        if (known.isPresent()) {
            return known;
        }
        return fetch(barcode).map(foods::save);
    }

    /** Draft item for the review form: one serving if known, else 100 g. */
    public static MealItemForm item(Food food) {
        int grams = food.getServingG() != null && food.getServingG() > 0 ? food.getServingG() : 100;
        String hint = food.getServingHint() != null ? "Portion laut Packung: " + food.getServingHint() : "Menge anpassen";
        return new MealItemForm(displayName(food), grams, food.getKcalPer100g(), food.getProteinPer100g(),
                food.getCarbsPer100g(), food.getFatPer100g(), false, hint.length() > 255 ? hint.substring(0, 255) : hint);
    }

    public static String displayName(Food food) {
        String name = food.getBrand() != null && !food.getBrand().isBlank() && !food.getName().contains(food.getBrand())
                ? food.getName() + " (" + food.getBrand() + ")" : food.getName();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    /** Keeps the values you saved for a scanned product (first item of the meal). */
    @Transactional
    public void remember(String barcode, MealItemForm item) {
        if (!validBarcode(barcode) || item == null) {
            return;
        }
        Food food = foods.findById(barcode).orElseGet(() -> {
            Food created = new Food();
            created.setBarcode(barcode);
            return created;
        });
        boolean changed = food.getKcalPer100g() == null
                || food.getKcalPer100g().compareTo(item.getKcalPer100g()) != 0
                || food.getProteinPer100g().compareTo(item.getProteinPer100g()) != 0
                || food.getCarbsPer100g().compareTo(item.getCarbsPer100g()) != 0
                || food.getFatPer100g().compareTo(item.getFatPer100g()) != 0;
        if (food.getName() == null) {
            food.setName(item.getName().trim());
        }
        if (!changed) {
            return;
        }
        food.setKcalPer100g(item.getKcalPer100g());
        food.setProteinPer100g(item.getProteinPer100g());
        food.setCarbsPer100g(item.getCarbsPer100g());
        food.setFatPer100g(item.getFatPer100g());
        food.setSource(Food.Source.USER);
        food.setUpdatedAt(LocalDateTime.now(clock));
        foods.save(food);
    }

    Optional<Food> fetch(String barcode) {
        URI uri = URI.create(baseUrl + "/api/v2/product/" + barcode
                + ".json?fields=product_name,product_name_de,brands,nutriments,serving_quantity,serving_size");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "plan-man/1.0 (private household app)")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LookupException("Open Food Facts ist gerade nicht erreichbar. Bitte per Satz oder Foto erfassen.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LookupException("Abgebrochen", e);
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new LookupException("Open Food Facts antwortet mit HTTP " + response.statusCode()
                    + ". Bitte per Satz oder Foto erfassen.", null);
        }
        return parse(barcode, response.body());
    }

    Optional<Food> parse(String barcode, String body) {
        JsonNode root = JSON.readTree(body);
        JsonNode product = root.path("product");
        if (root.path("status").asInt(0) != 1 || product.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode n = product.path("nutriments");
        BigDecimal kcal = decimal(n, "energy-kcal_100g");
        if (kcal == null) {
            BigDecimal kj = decimal(n, "energy-kj_100g");
            if (kj == null) {
                kj = decimal(n, "energy_100g");
            }
            kcal = kj == null ? null : kj.divide(KJ_PER_KCAL, 1, RoundingMode.HALF_UP);
        }
        String name = text(product, "product_name_de");
        if (name == null) {
            name = text(product, "product_name");
        }
        if (kcal == null || name == null) {
            log.info("Open Food Facts has {} but without name or energy", barcode);
            return Optional.empty();
        }
        Food food = new Food();
        food.setBarcode(barcode);
        food.setName(name.length() > 255 ? name.substring(0, 255) : name);
        String brand = text(product, "brands");
        if (brand != null) {
            brand = brand.split(",")[0].trim();
            food.setBrand(brand.length() > 255 ? brand.substring(0, 255) : brand);
        }
        food.setKcalPer100g(clamp(kcal, 900));
        food.setProteinPer100g(clamp(orZero(decimal(n, "proteins_100g")), 100));
        food.setCarbsPer100g(clamp(orZero(decimal(n, "carbohydrates_100g")), 100));
        food.setFatPer100g(clamp(orZero(decimal(n, "fat_100g")), 100));
        BigDecimal serving = decimal(product, "serving_quantity");
        if (serving != null && serving.signum() > 0 && serving.compareTo(BigDecimal.valueOf(5000)) <= 0) {
            food.setServingG(serving.setScale(0, RoundingMode.HALF_UP).intValue());
        }
        String servingHint = text(product, "serving_size");
        if (servingHint != null) {
            food.setServingHint(servingHint.length() > 255 ? servingHint.substring(0, 255) : servingHint);
        }
        food.setSource(Food.Source.OFF);
        food.setUpdatedAt(LocalDateTime.now(clock));
        return Optional.of(food);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return value.decimalValue().setScale(1, RoundingMode.HALF_UP);
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.asString().trim().replace(',', '.')).setScale(1, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        String text = value.isString() ? value.asString().trim() : null;
        return text == null || text.isEmpty() ? null : text;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal clamp(BigDecimal value, int max) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(BigDecimal.valueOf(max));
    }
}
