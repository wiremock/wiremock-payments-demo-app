package wiremock.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
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

    public DemoBff(String paymentsApiBaseUrl) {
        this.paymentsApiBaseUrl = paymentsApiBaseUrl;
    }

    void start() {
        webapp = Javalin.create(config -> config.jsonMapper(new JavalinJackson()))
                .post("/payments", this::handleCreatePayment)
                .start(0);
    }

    private void handleCreatePayment(Context ctx) {
        var productPaymentRequest = ctx.bodyAsClass(ProductPaymentRequest.class);
        var price = productCatalogue.getPrice(productPaymentRequest.productId(), productPaymentRequest.currency());
        var total = price * productPaymentRequest.quantity();

        ChargeRequest chargeRequest = new ChargeRequest(productPaymentRequest.customerId(), total, productPaymentRequest.currency());

        try {
            ChargeResponse chargeResponse =
                    Retry.of("payment-service", RetryConfig.custom().maxAttempts(3).build())
                    .executeCallable(() -> executePaymentRequest(chargeRequest));
            ctx.status(201);
            ctx.json(new PaymentResult(chargeResponse.status()));
        } catch (Exception e) {
            ctx.status(500);
            ctx.json(new PaymentResult(e.getMessage()));
        }
    }

    private ChargeResponse executePaymentRequest(ChargeRequest chargeRequest) throws IOException {
        Response response;
        try {
            response = httpClient.newCall(new Request.Builder()
                            .url(paymentsApiBaseUrl + "/charges")
                            .post(RequestBody.create(MediaType.parse("application/json"), toJson(chargeRequest)))
                            .build())
                    .execute();
        } catch (IOException e) {
            throw new IOException("Payment service fault");
        }

        if (response.isSuccessful()) {
            return objectMapper.readValue(response.body().bytes(), ChargeResponse.class);
        }

        throw new IOException("Payment service error");
    }

    private byte[] toJson(Object obj) {
        return Exceptions.uncheck(() -> objectMapper.writeValueAsBytes(obj), byte[].class);
    }

    public int getPort() {
        return webapp.port();
    }
}
