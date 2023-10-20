package wiremock.demo;

public record ProductPaymentRequest(String customerId, String productId, int quantity, String currency) {
}
