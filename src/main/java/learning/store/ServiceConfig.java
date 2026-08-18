package learning.store;

public record ServiceConfig(String apiKey, String publicBaseUrl, int port) {
    public static ServiceConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String publicUrl = required("PUBLIC_BASE_URL").replaceAll("/$", "");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new ServiceConfig(key, publicUrl, port);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
