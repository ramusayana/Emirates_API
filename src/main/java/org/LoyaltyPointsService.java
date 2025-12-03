package com.emirates.loyalty;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class LoyaltyPointsService extends AbstractVerticle {

    public static void main(String[] args) {
        io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
        vertx.deployVerticle(new LoyaltyPointsService());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.post("/v1/points/quote").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();

            // Basic validation
            if (body == null || !body.containsKey("fareAmount")) {
                ctx.response().setStatusCode(400).end(
                        new JsonObject().put("error", "Missing fareAmount").encode()
                );
                return;
            }

            double fareAmount = body.getDouble("fareAmount", 0.0);
            String currency = body.getString("currency", "USD");
            String cabinClass = body.getString("cabinClass", "ECONOMY");
            String customerTier = body.getString("customerTier", "NONE");
            String promoCode = body.getString("promoCode", null);

            int basePoints = (int) Math.floor(fareAmount);

            int tierBonusPct = switch (customerTier.toUpperCase()) {
                case "SILVER" -> 15;
                case "GOLD" -> 25;
                case "PLATINUM" -> 50;
                default -> 0;
            };
            int tierBonus = (basePoints * tierBonusPct) / 100;

            int promoBonus = 0;
            boolean promoExpiring = false;
            if (promoCode != null && promoCode.equalsIgnoreCase("SUMMER25")) {
                promoBonus = (basePoints * 25) / 100;
                promoExpiring = true;
            }

            double effectiveFxRate = lookupFxRate(currency);

            int totalPoints = basePoints + tierBonus + promoBonus;

            JsonObject response = new JsonObject()
                    .put("basePoints", basePoints)
                    .put("tierBonus", tierBonus)
                    .put("promoBonus", promoBonus)
                    .put("totalPoints", totalPoints)
                    .put("effectiveFxRate", round(effectiveFxRate, 2));

            JsonArray warnings = new JsonArray();
            if (promoExpiring) warnings.add("PROMO_EXPIRES_SOON");
            response.put("warnings", warnings);

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encodePrettily());
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080)
                .onSuccess(server -> {
                    System.out.println("Loyalty Points Service running on http://localhost:8080");
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private double lookupFxRate(String currency) {
        if (currency == null) return 1.0;
        return switch (currency.toUpperCase()) {
            case "USD" -> 1.0;
            case "EUR" -> 0.92;
            case "INR" -> 83.5;
            default -> 1.0;
        };
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
