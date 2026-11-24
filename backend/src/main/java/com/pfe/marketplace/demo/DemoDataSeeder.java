package com.pfe.marketplace.demo;

import com.pfe.marketplace.catalog.service.ImageStorageService;
import com.pfe.marketplace.common.util.Slugs;
import com.pfe.marketplace.demo.DemoCatalog.CategoryDef;
import com.pfe.marketplace.demo.DemoCatalog.ProductDef;
import com.pfe.marketplace.demo.DemoCatalog.ShopDef;
import com.pfe.marketplace.demo.DemoCopy.ProductCopy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a realistic, deterministic demo dataset (profile {@code demo}) on an empty database: 1 admin, 8 sellers,
 * 40 customers, 120 products, about a year of orders with seasonality, reviews and disputes. COD refusals follow
 * learnable patterns (risky customers, large baskets, first orders, late-night orders) so the AI return-risk model
 * has signal to train on. Rows are inserted with JDBC to control historical timestamps.
 */
@Slf4j
@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    static final String ADMIN_EMAIL = "admin@markethub.test";
    private static final int CUSTOMERS = 40;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int HISTORY_DAYS = 365;
    private static final String[] FIRST_NAMES = {"Emma", "Lucas", "Chloé", "Hugo", "Léa", "Louis", "Manon", "Gabriel",
            "Inès", "Adam", "Jade", "Arthur", "Sarah", "Nathan", "Lina", "Yanis", "Camille", "Noah", "Zoé", "Rayan",
            "Alice", "Mehdi", "Clara", "Paul", "Nora", "Tom", "Amira", "Jules", "Eva", "Sami"};
    private static final String[] LAST_NAMES = {"Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit",
            "Durand", "Leroy", "Moreau", "Simon", "Laurent", "Lefebvre", "Michel", "Garcia", "Benali", "Roux", "Fournier",
            "Girard", "Bonnet", "Dupont", "Lambert", "Fontaine", "Rousseau", "Haddad"};
    private static final String[] CITIES = {"Paris", "Lyon", "Marseille", "Toulouse", "Lille", "Bordeaux", "Nantes",
            "Nice", "Strasbourg", "Montpellier", "Rennes", "Grenoble"};
    private static final Set<String> RISKY_CITIES = Set.of("Marseille", "Nice", "Grenoble");
    private static final String[] STREETS = {"rue de la République", "avenue Jean Jaurès", "rue Victor Hugo",
            "boulevard Voltaire", "rue Pasteur", "rue des Lilas", "avenue de la Gare", "place du Marché"};
    private static final String[][] REVIEW_COMMENTS = {
            {"Very disappointed, broke after a week.", "Not as described.", "Poor quality for the price."},
            {"Below my expectations.", "Okay but feels cheap.", "Delivery was slow and the item is average."},
            {"Decent product, does the job.", "Fine for the price.", "Average, nothing special."},
            {"Good quality, happy with it.", "Works well, fast delivery.", "Nice product, would recommend."},
            {"Excellent! Exactly what I needed.", "Superb quality, five stars.", "Love it, already ordered another one."}};
    private static final String[] RETURN_REASONS = {"The item arrived damaged.", "Wrong size, I need to return it.",
            "The product does not match the description.", "I received the wrong colour."};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ImageStorageService storage;
    private final Clock clock;

    private final Random random = new Random(42);
    private final Map<String, Long> categoryIds = new HashMap<>();
    private final List<SeedShop> shops = new ArrayList<>();
    private final List<SeedProduct> products = new ArrayList<>();
    private final Map<String, List<String>> pairings = DemoCopy.loadPairings();
    private final List<SeedCustomer> customers = new ArrayList<>();
    private final Rows rows = new Rows();
    private final Ids ids = new Ids();
    private Instant now;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ImageStorageService storage, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = jdbc.queryForObject("select count(*) from users where email = ?", Long.class, ADMIN_EMAIL);
        if (existing != null && existing > 0) {
            log.info("Demo data already present, skipping seed");
            return;
        }
        long start = System.currentTimeMillis();
        now = clock.instant();
        ids.init(jdbc);
        seedUsers();
        seedCategories();
        seedShops();
        seedProducts();
        seedOrders();
        rows.flush(jdbc);
        refreshAggregates();
        ids.resetSequences(jdbc);
        log.info("Demo data seeded in {} ms: {} products, {} orders", System.currentTimeMillis() - start,
                products.size(), rows.orders.size());
    }

    // ------------------------------------------------------------------ users

    private void seedUsers() {
        insertUser(ADMIN_EMAIL, passwordEncoder.encode("Admin123!"), "Amina", "Admin", "ADMIN", now.minus(days(420)));
        String sellerHash = passwordEncoder.encode("Seller123!");
        for (int i = 1; i <= DemoCatalog.SHOPS.size(); i++) {
            long id = insertUser("seller" + i + "@markethub.test", sellerHash, pick(FIRST_NAMES), pick(LAST_NAMES),
                    "SELLER", now.minus(days(410 - i)));
            shops.add(new SeedShop(id, -1, DemoCatalog.SHOPS.get(i - 1)));
        }
        String customerHash = passwordEncoder.encode("Customer123!");
        for (int i = 1; i <= CUSTOMERS; i++) {
            // Customer 1 is the demo account: reliable and present from the start.
            Instant created = i == 1 ? now.minus(days(400)) : now.minus(days(10 + random.nextInt(400)));
            String first = FIRST_NAMES[(i * 7) % FIRST_NAMES.length];
            String last = LAST_NAMES[(i * 11) % LAST_NAMES.length];
            long id = insertUser("customer" + i + "@markethub.test", customerHash, first, last, "CUSTOMER", created);
            boolean risky = i != 1 && random.nextDouble() < 0.18;
            List<String> favourites = List.of(pickParentCategory(), pickParentCategory());
            customers.add(new SeedCustomer(id, first + " " + last, pick(CITIES), phone(), created, risky, favourites));
        }
    }

    private long insertUser(String email, String hash, String first, String last, String role, Instant created) {
        long id = ids.next("users");
        rows.users.add(new Object[]{id, email, hash, first, last, phone(), role, ts(created), ts(created)});
        return id;
    }

    // ------------------------------------------------------------- catalog

    private void seedCategories() {
        for (CategoryDef def : DemoCatalog.CATEGORIES) {
            long id = ids.next("categories");
            categoryIds.put(def.key(), id);
            Long parent = def.parentKey() == null ? null : categoryIds.get(def.parentKey());
            rows.categories.add(new Object[]{id, parent, def.name(), Slugs.slugify(def.name()),
                    "Discover our selection of " + def.name().toLowerCase() + ".", ts(now.minus(days(420)))});
        }
    }

    private void seedShops() {
        for (int i = 0; i < shops.size(); i++) {
            SeedShop shop = shops.get(i);
            long id = ids.next("shops");
            shops.set(i, new SeedShop(shop.ownerId(), id, shop.def()));
            boolean pending = i == shops.size() - 1;
            BigDecimal rate = shop.def().commissionRate() == null ? null : new BigDecimal(shop.def().commissionRate());
            Instant created = now.minus(days(405 - i));
            rows.shops.add(new Object[]{id, shop.ownerId(), shop.def().name(), Slugs.slugify(shop.def().name()),
                    shop.def().description(), pending ? "PENDING" : "APPROVED", rate, ts(created), ts(created)});
        }
    }

    private void seedProducts() {
        Map<String, SeedShop> shopByCategory = new HashMap<>();
        shops.stream().filter(s -> !s.def().name().equals("Green Corner"))
                .forEach(s -> s.def().categoryKeys().forEach(key -> shopByCategory.putIfAbsent(key, s)));
        Map<String, String> parentOf = new HashMap<>();
        Map<String, String> colors = new HashMap<>();
        DemoCatalog.CATEGORIES.forEach(c -> {
            parentOf.put(c.key(), c.parentKey() == null ? c.key() : c.parentKey());
            colors.put(c.key(), c.color());
        });

        Map<String, ProductCopy> copy = DemoCopy.load();
        int index = 0;
        for (ProductDef def : DemoCatalog.PRODUCTS) {
            index++;
            long id = ids.next("products");
            SeedShop shop = shopByCategory.get(def.categoryKey());
            boolean newArrival = index % 10 == 0;
            Instant created = newArrival ? now.minus(days(5 + random.nextInt(25))) : now.minus(days(380 + random.nextInt(20)));
            BigDecimal price = BigDecimal.valueOf(def.price()).setScale(2, RoundingMode.HALF_UP);
            double quality = 3.7 + random.nextDouble() * 1.25;
            int stock = stockLevel(index);
            ProductCopy text = copy.get(def.name());
            if (text == null) {
                throw new IllegalStateException("No demo copy for product " + def.name());
            }
            rows.products.add(new Object[]{id, shop.shopId(), categoryIds.get(def.categoryKey()), def.name(),
                    text.description().isEmpty() ? def.blurb() : text.description(), price, text.wasPrice(),
                    text.tagline() == null ? def.blurb() : text.tagline(), json(text.highlights()), json(text.specs()),
                    stock, true, ts(created), ts(created)});
            for (int variant = 0; variant < 2; variant++) {
                String url = writeImage(index, variant, text.art(), text.color(), colors.get(def.categoryKey()));
                rows.images.add(new Object[]{ids.next("product_images"), id, url, variant});
            }
            products.add(new SeedProduct(id, shop.shopId(), shop.commissionRate(), parentOf.get(def.categoryKey()),
                    def.name(), price, quality, created));
        }
    }

    private int stockLevel(int index) {
        if (index % 37 == 0) {
            return 0;
        }
        if (index % 13 == 0) {
            return 1 + random.nextInt(4);
        }
        return 8 + random.nextInt(140);
    }

    // -------------------------------------------------------------- orders

    private void seedOrders() {
        Map<Long, Integer> previousOrders = new HashMap<>();
        Set<String> reviewed = new HashSet<>();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (int daysAgo = HISTORY_DAYS; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            int count = poisson(expectedOrders(day, daysAgo));
            Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            List<SeedCustomer> eligible = customers.stream().filter(c -> c.createdAt().isBefore(dayStart)).toList();
            if (eligible.isEmpty()) {
                continue;
            }
            List<Map.Entry<Instant, SeedCustomer>> dayOrders = new ArrayList<>();
            for (int n = 0; n < count; n++) {
                SeedCustomer customer = weightedCustomer(eligible);
                Instant placedAt = dayStart.plus(Duration.ofMinutes(orderMinute(customer.risky())));
                if (placedAt.isBefore(now.minus(Duration.ofMinutes(30)))) {
                    dayOrders.add(Map.entry(placedAt, customer));
                }
            }
            dayOrders.sort(Map.Entry.comparingByKey());
            for (Map.Entry<Instant, SeedCustomer> entry : dayOrders) {
                int prior = previousOrders.merge(entry.getValue().id(), 1, Integer::sum) - 1;
                createOrder(entry.getValue(), entry.getKey(), prior, reviewed);
            }
        }
    }

    /** Growth over the year, busier week-ends, a Black-Friday/Christmas peak and a quiet January. */
    private double expectedOrders(LocalDate day, int daysAgo) {
        double trend = 1.0 + 1.1 * (HISTORY_DAYS - daysAgo) / HISTORY_DAYS;
        double weekly = switch (day.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> 1.35;
            case FRIDAY -> 1.15;
            case MONDAY -> 0.85;
            default -> 1.0;
        };
        double seasonal = switch (day.getMonth()) {
            case NOVEMBER -> 1.45;
            case DECEMBER -> day.getDayOfMonth() <= 22 ? 1.7 : 1.1;
            case JANUARY -> 0.8;
            case JULY, AUGUST -> 1.1;
            default -> 1.0;
        };
        return 1.05 * trend * weekly * seasonal;
    }

    private void createOrder(SeedCustomer customer, Instant placedAt, int priorOrders, Set<String> reviewed) {
        List<SeedProduct> basket = pickBasket(customer, placedAt);
        if (basket.isEmpty()) {
            return;
        }
        Map<Long, List<Object[]>> linesByShop = new LinkedHashMap<>();
        Map<Long, BigDecimal> subtotalByShop = new LinkedHashMap<>();
        Map<Long, BigDecimal> rateByShop = new HashMap<>();
        for (SeedProduct product : basket) {
            int quantity = random.nextDouble() < 0.15 ? 2 : 1;
            BigDecimal line = product.price().multiply(BigDecimal.valueOf(quantity));
            linesByShop.computeIfAbsent(product.shopId(), k -> new ArrayList<>())
                    .add(new Object[]{product, quantity, line});
            subtotalByShop.merge(product.shopId(), line, BigDecimal::add);
            rateByShop.put(product.shopId(), product.commissionRate() == null ? BigDecimal.TEN : product.commissionRate());
        }
        BigDecimal subtotal = subtotalByShop.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = subtotal.compareTo(BigDecimal.valueOf(50)) >= 0 ? BigDecimal.ZERO.setScale(2) : new BigDecimal("4.99");
        BigDecimal total = subtotal.add(shipping);
        boolean cod = random.nextDouble() < (customer.risky() ? 0.85 : 0.58);
        int hour = placedAt.atOffset(ZoneOffset.UTC).getHour();
        double refusalProbability = cod ? refusalProbability(customer, total, priorOrders, hour) : 0.025;
        long ageDays = Duration.between(placedAt, now).toDays();

        long orderId = ids.next("orders");
        List<String> statuses = new ArrayList<>();
        Instant lastUpdate = placedAt;
        for (Map.Entry<Long, List<Object[]>> entry : linesByShop.entrySet()) {
            long shopOrderId = ids.next("shop_orders");
            Timeline timeline = timeline(placedAt, ageDays, refusalProbability, cod);
            statuses.add(timeline.finalStatus());
            BigDecimal shopSubtotal = subtotalByShop.get(entry.getKey());
            BigDecimal rate = rateByShop.get(entry.getKey()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal commission = shopSubtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            String tracking = timeline.reached("SHIPPED") ? "TRK" + (100000 + random.nextInt(899999)) : null;
            rows.shopOrders.add(new Object[]{shopOrderId, orderId, entry.getKey(), timeline.finalStatus(), shopSubtotal,
                    rate, commission, tracking, ts(placedAt), ts(timeline.lastAt())});
            for (Object[] line : entry.getValue()) {
                SeedProduct product = (SeedProduct) line[0];
                rows.items.add(new Object[]{ids.next("order_items"), shopOrderId, product.id(), product.name(),
                        product.price(), line[1], line[2]});
                if (timeline.finalStatus().equals("DELIVERED")) {
                    maybeReview(customer, product, timeline.lastAt(), reviewed);
                }
            }
            timeline.steps().forEach(step -> rows.history.add(new Object[]{ids.next("order_status_history"),
                    shopOrderId, step.status(), step.note(), ts(step.at())}));
            maybeDispute(customer, shopOrderId, timeline, cod);
            if (timeline.lastAt().isAfter(lastUpdate)) {
                lastUpdate = timeline.lastAt();
            }
        }
        String paymentStatus = paymentStatus(cod, statuses);
        rows.orders.add(new Object[]{orderId, reference(placedAt), customer.id(), cod ? "COD" : "CARD", paymentStatus,
                subtotal, shipping, total, customer.name(), customer.phone(),
                (1 + random.nextInt(120)) + " " + pick(STREETS), customer.city(), postalCode(), "France",
                ts(placedAt), ts(lastUpdate)});
        if (!cod) {
            rows.payments.add(new Object[]{ids.next("payments"), orderId, "STRIPE_MOCK",
                    "mock_pi_seed_" + orderId, total, "eur", paymentStatus.equals("REFUNDED") ? "REFUNDED" : "SUCCEEDED",
                    ts(placedAt), ts(placedAt)});
        }
    }

    private double refusalProbability(SeedCustomer customer, BigDecimal total, int priorOrders, int hour) {
        double p = 0.04;
        if (customer.risky()) {
            p += 0.32;
        }
        if (total.compareTo(BigDecimal.valueOf(150)) > 0) {
            p += 0.09;
        }
        if (priorOrders == 0) {
            p += 0.08;
        }
        if (hour >= 23 || hour < 6) {
            p += 0.06;
        }
        if (RISKY_CITIES.contains(customer.city())) {
            p += 0.04;
        }
        return Math.min(p, 0.8);
    }

    private Timeline timeline(Instant placedAt, long ageDays, double refusalProbability, boolean cod) {
        Timeline t = new Timeline(placedAt);
        if (random.nextDouble() < 0.04) {
            return t.step("CANCELLED", hoursLater(t.lastAt(), 1, 20), "Cancelled by customer").capped(now);
        }
        t.step("CONFIRMED", hoursLater(placedAt, 2, 26), null);
        if (ageDays < 2) {
            return random.nextDouble() < 0.55 ? new Timeline(placedAt) : t.capped(now);
        }
        t.step("SHIPPED", hoursLater(t.lastAt(), 12, 48), null);
        if (ageDays < 4) {
            return random.nextDouble() < 0.4 ? t.dropLast().capped(now) : t.capped(now);
        }
        boolean refused = cod && random.nextDouble() < refusalProbability;
        if (refused) {
            return t.step("RETURNED", hoursLater(t.lastAt(), 24, 96), "Customer refused the parcel").capped(now);
        }
        if (ageDays < 7 && random.nextDouble() < 0.35) {
            return t.capped(now);
        }
        t.step("DELIVERED", hoursLater(t.lastAt(), 24, 96), null);
        if (!cod && ageDays > 20 && random.nextDouble() < refusalProbability) {
            t.step("RETURNED", hoursLater(t.lastAt(), 48, 240), "Return approved by the platform");
        }
        return t.capped(now);
    }

    private void maybeDispute(SeedCustomer customer, long shopOrderId, Timeline timeline, boolean cod) {
        String status = timeline.finalStatus();
        Instant at = timeline.lastAt();
        if (status.equals("RETURNED") && !cod) {
            rows.disputes.add(new Object[]{ids.next("disputes"), shopOrderId, customer.id(), "RETURN",
                    pick(RETURN_REASONS), "APPROVED", "Refund issued", ts(at.minus(days(2))), ts(at)});
        } else if (status.equals("DELIVERED") && Duration.between(at, now).toDays() < 12 && random.nextDouble() < 0.12) {
            rows.disputes.add(new Object[]{ids.next("disputes"), shopOrderId, customer.id(), "RETURN",
                    pick(RETURN_REASONS), "OPEN", null, ts(at.plus(Duration.ofHours(30)).isAfter(now) ? now : at.plus(Duration.ofHours(30))), null});
        } else if (status.equals("DELIVERED") && random.nextDouble() < 0.006) {
            rows.disputes.add(new Object[]{ids.next("disputes"), shopOrderId, customer.id(), "DISPUTE",
                    "The parcel arrived late and the box was open.", "REJECTED",
                    "Carrier confirmed an intact delivery", ts(at.plus(days(1))), ts(at.plus(days(3)))});
        }
    }

    private void maybeReview(SeedCustomer customer, SeedProduct product, Instant deliveredAt, Set<String> reviewed) {
        String key = customer.id() + ":" + product.id();
        if (random.nextDouble() > 0.38 || !reviewed.add(key)) {
            return;
        }
        int rating = (int) Math.round(product.quality() + random.nextGaussian() * 0.8);
        rating = Math.clamp(rating, 1, 5);
        Instant at = deliveredAt.plus(Duration.ofHours(12 + random.nextInt(200)));
        rows.reviews.add(new Object[]{ids.next("reviews"), product.id(), customer.id(), rating,
                pick(REVIEW_COMMENTS[rating - 1]), ts(at.isAfter(now) ? now : at)});
    }

    private List<SeedProduct> pickBasket(SeedCustomer customer, Instant at) {
        double r = random.nextDouble();
        int size = r < 0.58 ? 1 : r < 0.88 ? 2 : 3;
        List<SeedProduct> available = products.stream().filter(p -> p.createdAt().isBefore(at)).toList();
        List<SeedProduct> basket = new ArrayList<>();
        for (int i = 0; i < size * 4 && basket.size() < size; i++) {
            SeedProduct companion = basket.size() == 1 && random.nextDouble() < 0.75
                    ? companionOf(basket.getFirst(), available) : null;
            if (companion != null && !basket.contains(companion)) {
                basket.add(companion);
                continue;
            }
            String parent = random.nextDouble() < 0.75 ? customer.favourites().get(random.nextInt(2)) : null;
            List<SeedProduct> pool = parent == null ? available
                    : available.stream().filter(p -> p.parentCategory().equals(parent)).toList();
            if (pool.isEmpty()) {
                continue;
            }
            SeedProduct candidate = popularityPick(pool);
            if (!basket.contains(candidate)) {
                basket.add(candidate);
            }
        }
        return basket;
    }

    /** A product typically bought with this one (phone → case), so "bought together" data looks real. */
    private SeedProduct companionOf(SeedProduct product, List<SeedProduct> available) {
        List<String> names = pairings.getOrDefault(product.name(), List.of());
        List<SeedProduct> candidates = available.stream().filter(p -> names.contains(p.name())).toList();
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** Products earlier in a category list are more popular (a long-tail distribution). */
    private SeedProduct popularityPick(List<SeedProduct> pool) {
        double u = random.nextDouble();
        return pool.get(Math.min(pool.size() - 1, (int) (pool.size() * u * u)));
    }

    private SeedCustomer weightedCustomer(List<SeedCustomer> eligible) {
        double u = random.nextDouble();
        int index = (int) (eligible.size() * Math.pow(u, 1.6));
        return eligible.get(Math.min(index, eligible.size() - 1));
    }

    private static String paymentStatus(boolean cod, List<String> statuses) {
        boolean anyDelivered = statuses.contains("DELIVERED");
        boolean allReleased = statuses.stream().allMatch(s -> s.equals("CANCELLED") || s.equals("RETURNED"));
        if (cod) {
            return anyDelivered ? "PAID" : allReleased ? "FAILED" : "PENDING";
        }
        return allReleased ? "REFUNDED" : "PAID";
    }

    // ---------------------------------------------------------- aggregates

    private void refreshAggregates() {
        jdbc.update("""
                update products p set sold_count = coalesce((
                    select sum(oi.quantity) from order_items oi join shop_orders so on so.id = oi.shop_order_id
                    where oi.product_id = p.id and so.status in ('PENDING','CONFIRMED','SHIPPED','DELIVERED')), 0)""");
        jdbc.update("""
                update products p set review_count = r.cnt, avg_rating = r.avg
                from (select product_id, count(*) as cnt, round(avg(rating), 2) as avg from reviews group by product_id) r
                where r.product_id = p.id""");
    }

    // ---------------------------------------------------------------- utils

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String writeImage(int index, int variant, String art, String color, String categoryColor) {
        String file = "p" + index + "-" + variant + ".svg";
        Path path = storage.root().resolve("demo").resolve(file);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, DemoImages.illustration(art, color, categoryColor, variant), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ImageStorageService.PUBLIC_PREFIX + "demo/" + file;
    }

    private String reference(Instant at) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            suffix.append(chars.charAt(random.nextInt(chars.length())));
        }
        return "MH-" + DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC).format(at) + "-" + suffix;
    }

    /** Risky customers order late at night more often, a weak but observable signal for the AI model. */
    private int orderMinute(boolean risky) {
        if (random.nextDouble() < (risky ? 0.25 : 0.06)) {
            int hour = random.nextInt(7);
            return (hour == 6 ? 23 : hour) * 60 + random.nextInt(60);
        }
        int hour = random.nextDouble() < 0.45 ? 9 + random.nextInt(9) : 17 + random.nextInt(6);
        return hour * 60 + random.nextInt(60);
    }

    private int poisson(double lambda) {
        double limit = Math.exp(-lambda);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            product *= random.nextDouble();
            count++;
        }
        return count;
    }

    private Instant hoursLater(Instant from, int min, int max) {
        return from.plus(Duration.ofMinutes(60L * min + random.nextInt(60 * (max - min))));
    }

    private String pickParentCategory() {
        List<CategoryDef> parents = DemoCatalog.CATEGORIES.stream().filter(c -> c.parentKey() == null).toList();
        return parents.get(random.nextInt(parents.size())).key();
    }

    private String phone() {
        return "+33 6 " + (10 + random.nextInt(89)) + " " + (10 + random.nextInt(89)) + " " + (10 + random.nextInt(89))
                + " " + (10 + random.nextInt(89));
    }

    private String postalCode() {
        return String.valueOf(10000 + random.nextInt(85000));
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static Duration days(long days) {
        return Duration.ofDays(days);
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    // ---------------------------------------------------------------- types

    private record SeedShop(long ownerId, long shopId, ShopDef def) {
        BigDecimal commissionRate() {
            return def.commissionRate() == null ? null : new BigDecimal(def.commissionRate());
        }
    }

    private record SeedProduct(long id, long shopId, BigDecimal commissionRate, String parentCategory, String name,
                               BigDecimal price, double quality, Instant createdAt) {
    }

    private record SeedCustomer(long id, String name, String city, String phone, Instant createdAt, boolean risky,
                                List<String> favourites) {
    }

    private record Step(String status, Instant at, String note) {
    }

    /** Ordered status steps of one shop order; never extends past "now". */
    private static final class Timeline {
        private final List<Step> steps = new ArrayList<>();

        Timeline(Instant placedAt) {
            steps.add(new Step("PENDING", placedAt, "Order placed"));
        }

        Timeline step(String status, Instant at, String note) {
            steps.add(new Step(status, at, note));
            return this;
        }

        Timeline dropLast() {
            steps.removeLast();
            return this;
        }

        Timeline capped(Instant now) {
            steps.removeIf(s -> s.at().isAfter(now));
            return this;
        }

        List<Step> steps() {
            return steps;
        }

        String finalStatus() {
            return steps.getLast().status();
        }

        Instant lastAt() {
            return steps.getLast().at();
        }

        boolean reached(String status) {
            return steps.stream().anyMatch(s -> s.status().equals(status));
        }
    }

    /** Collected rows, written with batch inserts once generation is complete. */
    private static final class Rows {
        final List<Object[]> users = new ArrayList<>();
        final List<Object[]> categories = new ArrayList<>();
        final List<Object[]> shops = new ArrayList<>();
        final List<Object[]> products = new ArrayList<>();
        final List<Object[]> images = new ArrayList<>();
        final List<Object[]> orders = new ArrayList<>();
        final List<Object[]> shopOrders = new ArrayList<>();
        final List<Object[]> items = new ArrayList<>();
        final List<Object[]> history = new ArrayList<>();
        final List<Object[]> payments = new ArrayList<>();
        final List<Object[]> reviews = new ArrayList<>();
        final List<Object[]> disputes = new ArrayList<>();

        void flush(JdbcTemplate jdbc) {
            jdbc.batchUpdate("""
                    insert into users (id, email, password_hash, first_name, last_name, phone, role, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?)""", users);
            jdbc.batchUpdate("insert into categories (id, parent_id, name, slug, description, created_at) values (?,?,?,?,?,?)",
                    categories);
            jdbc.batchUpdate("""
                    insert into shops (id, owner_id, name, slug, description, status, commission_rate, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?)""", shops);
            jdbc.batchUpdate("""
                    insert into products (id, shop_id, category_id, name, description, price, compare_at_price, tagline,
                        highlights, specs, stock, active, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?)""", products);
            jdbc.batchUpdate("insert into product_images (id, product_id, url, position) values (?,?,?,?)", images);
            jdbc.batchUpdate("""
                    insert into orders (id, reference, customer_id, payment_method, payment_status, subtotal, shipping_fee,
                        total, shipping_full_name, shipping_phone, shipping_address_line, shipping_city,
                        shipping_postal_code, shipping_country, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", orders);
            jdbc.batchUpdate("""
                    insert into shop_orders (id, order_id, shop_id, status, subtotal, commission_rate, commission_amount,
                        tracking_number, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?)""", shopOrders);
            jdbc.batchUpdate("""
                    insert into order_items (id, shop_order_id, product_id, product_name, unit_price, quantity, line_total)
                    values (?,?,?,?,?,?,?)""", items);
            jdbc.batchUpdate("""
                    insert into order_status_history (id, shop_order_id, status, note, changed_at) values (?,?,?,?,?)""",
                    history);
            jdbc.batchUpdate("""
                    insert into payments (id, order_id, provider, provider_reference, amount, currency, status,
                        created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?)""", payments);
            jdbc.batchUpdate("insert into reviews (id, product_id, user_id, rating, comment, created_at) values (?,?,?,?,?,?)",
                    reviews);
            jdbc.batchUpdate("""
                    insert into disputes (id, shop_order_id, customer_id, type, reason, status, admin_note, created_at,
                        resolved_at)
                    values (?,?,?,?,?,?,?,?,?)""", disputes);
        }
    }

    /** Explicit ids so related rows can be generated before insertion; sequences are realigned afterwards. */
    private static final class Ids {
        private static final List<String> TABLES = List.of("users", "categories", "shops", "products",
                "product_images", "orders", "shop_orders", "order_items", "order_status_history", "payments",
                "reviews", "disputes");
        private final Map<String, Long> next = new HashMap<>();

        void init(JdbcTemplate jdbc) {
            for (String table : TABLES) {
                Long max = jdbc.queryForObject("select coalesce(max(id), 0) from " + table, Long.class);
                next.put(table, (max == null ? 0 : max) + 1);
            }
        }

        long next(String table) {
            return next.merge(table, 1L, Long::sum) - 1;
        }

        void resetSequences(JdbcTemplate jdbc) {
            for (String table : TABLES) {
                jdbc.execute("select setval(pg_get_serial_sequence('" + table + "', 'id'), "
                        + "greatest((select coalesce(max(id), 0) from " + table + "), 1))");
            }
        }
    }
}
