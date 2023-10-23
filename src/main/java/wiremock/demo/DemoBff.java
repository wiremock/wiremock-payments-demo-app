package wiremock.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import wiremock.demo.paymentservice.ChargeRequest;
import wiremock.demo.paymentservice.ChargeResponse;
import wiremock.demo.utils.Exceptions;

import java.io.IOException;

public class DemoBff {

    private final String paymentsApiBaseUrl;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProductCatalogue productCatalogue = new ProductCatalogue();

    private Javalin webapp;

    public DemoBff() {
        this("https://payments.example.com");
    }

    public DemoBff(String paymentsApiBaseUrl) {
        this.paymentsApiBaseUrl = paymentsApiBaseUrl;
    }

    void start() {
        webapp = Javalin.create(config -> config.jsonMapper(new JavalinJackson()))
                .post("/payments", this::handleCreatePayment)
                .start(0);
    }

    private void handleCreatePayment(Context ctx) throws IOException {
        var productPaymentRequest = ctx.bodyAsClass(ProductPaymentRequest.class);
        var price = productCatalogue.getPrice(productPaymentRequest.productId(), productPaymentRequest.currency());
        var total = price * productPaymentRequest.quantity();

        var chargeRequest = new ChargeRequest(productPaymentRequest.customerId(), total, productPaymentRequest.currency());

        Response response;
        try {
            response = httpClient.newCall(new Request.Builder()
                            .url(paymentsApiBaseUrl + "/charges")
                            .post(RequestBody.create(MediaType.parse("application/json"), toJson(chargeRequest)))
                            .build())
                    .execute();
        } catch (IOException e) {
            ctx.status(500);
            ctx.json(new PaymentResult("Payment service fault"));
            return;
        }

        if (response.isSuccessful()) {
            ChargeResponse chargeResponse = objectMapper.readValue(response.body().bytes(), ChargeResponse.class);
            ctx.status(201);
            ctx.json(new PaymentResult(chargeResponse.status()));
        } else if (response.code() >= 500) {
            ctx.status(500);
            ctx.json(new PaymentResult("Payment service error"));
        }
    }

    private byte[] toJson(Object obj) {
        return Exceptions.uncheck(() -> objectMapper.writeValueAsBytes(obj), byte[].class);
    }

    public int getPort() {
        return webapp.port();
    }

    public static void main(String[] args) {
        new DemoBff().start();
    }
}
