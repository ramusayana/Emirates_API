package com.emirates.loyalty;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Component tests for LoyaltyPointsService.
 * Spins up the real Vert.x HTTP server on a random port
 * and calls the /v1/points/quote endpoint via WebClient.
 */
public class LoyaltyPointsServiceAlternativeTest {

    private Vertx vertx;
    private WebClient client;
    private int port;

    @Before
    public void setUp() throws Exception {
        vertx = Vertx.vertx();
        // Deploy service and capture actual port (here we keep 8080 for simplicity,
        // but this can be randomized in a more advanced setup)
        vertx.deployVerticle(new LoyaltyPointsService())
                .toCompletionStage()
                .toCompletableFuture()
                .get();
        client = WebClient.create(vertx);
        port = 8080;
    }

    @After
    public void tearDown() throws Exception {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().get();
        }
    }

    @Test
    public void quote_shouldReturnExpectedPointsForSilverWithPromo() throws Exception {
        JsonObject payload = new JsonObject()
                .put("fareAmount", 1234.50)
                .put("currency", "USD")
                .put("cabinClass", "ECONOMY")
                .put("customerTier", "SILVER")
                .put("promoCode", "SUMMER25");

        HttpResponse<io.vertx.core.buffer.Buffer> response = client
                .post(port, "localhost", "/v1/points/quote")
                .sendJsonObject(payload)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(200, response.statusCode());
        JsonObject body = response.bodyAsJsonObject();

        // Base points = floor(1234.50) = 1234
        assertEquals(1234, body.getInteger("basePoints").intValue());
        // Silver tier = 15% of base = 185.1 -> 185
        assertEquals(185, body.getInteger("tierBonus").intValue());
        // Promo 25% of base = 308.5 -> 308
        assertEquals(308, body.getInteger("promoBonus").intValue());
        // Total = base + tier + promo
        assertEquals(1727, body.getInteger("totalPoints").intValue());

        assertNotNull(body.getDouble("effectiveFxRate"));
        JsonArray warnings = body.getJsonArray("warnings");
        assertNotNull(warnings);
        assertTrue(warnings.contains("PROMO_EXPIRES_SOON"));
    }

    @Test
    public void quote_shouldReturn400WhenFareAmountMissing() throws Exception {
        JsonObject payload = new JsonObject()
                .put("currency", "USD")
                .put("cabinClass", "ECONOMY")
                .put("customerTier", "NONE");

        HttpResponse<io.vertx.core.buffer.Buffer> response = client
                .post(port, "localhost", "/v1/points/quote")
                .sendJsonObject(payload)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(400, response.statusCode());
        JsonObject body = response.bodyAsJsonObject();
        assertEquals("Missing fareAmount", body.getString("error"));
    }

    @Test
    public void quote_shouldHandleDifferentTiersAndNoPromo() throws Exception {
        JsonObject payload = new JsonObject()
                .put("fareAmount", 1000.0)
                .put("currency", "USD")
                .put("cabinClass", "ECONOMY")
                .put("customerTier", "GOLD"); // 25% according to service

        HttpResponse<io.vertx.core.buffer.Buffer> response = client
                .post(port, "localhost", "/v1/points/quote")
                .sendJsonObject(payload)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(200, response.statusCode());
        JsonObject body = response.bodyAsJsonObject();

        // base = 1000
        assertEquals(1000, body.getInteger("basePoints").intValue());
        // GOLD = 25% of 1000 = 250
        assertEquals(250, body.getInteger("tierBonus").intValue());
        // No promo code -> 0
        assertEquals(0, body.getInteger("promoBonus").intValue());
        assertEquals(1250, body.getInteger("totalPoints").intValue());

        JsonArray warnings = body.getJsonArray("warnings");
        assertNotNull(warnings);
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void quote_shouldUseFxRateForNonUsdCurrency() throws Exception {
        JsonObject payload = new JsonObject()
                .put("fareAmount", 500.0)
                .put("currency", "EUR") // uses specific FX mapping in service
                .put("cabinClass", "ECONOMY")
                .put("customerTier", "NONE");

        HttpResponse<io.vertx.core.buffer.Buffer> response = client
                .post(port, "localhost", "/v1/points/quote")
                .sendJsonObject(payload)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(200, response.statusCode());
        JsonObject body = response.bodyAsJsonObject();

        assertEquals(500, body.getInteger("basePoints").intValue());
        assertEquals(0, body.getInteger("tierBonus").intValue());
        assertEquals(0, body.getInteger("promoBonus").intValue());

        Double fxRate = body.getDouble("effectiveFxRate");
        assertNotNull(fxRate);
        // Service defines EUR -> 0.92 and rounds to 2 decimals
        assertEquals(0.92, fxRate, 0.0);
    }

    @Test
    public void quote_shouldDefaultFieldsWhenNotProvided() throws Exception {
        JsonObject payload = new JsonObject()
                .put("fareAmount", 200.0);
        // currency, cabinClass, tier, promoCode omitted -> defaults apply

        HttpResponse<io.vertx.core.buffer.Buffer> response = client
                .post(port, "localhost", "/v1/points/quote")
                .sendJsonObject(payload)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        assertEquals(200, response.statusCode());
        JsonObject body = response.bodyAsJsonObject();

        // base = floor(200.0) = 200
        assertEquals(200, body.getInteger("basePoints").intValue());
        // default tier NONE -> 0
        assertEquals(0, body.getInteger("tierBonus").intValue());
        // no promo -> 0
        assertEquals(0, body.getInteger("promoBonus").intValue());
        assertEquals(200, body.getInteger("totalPoints").intValue());
    }
}
