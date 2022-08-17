package cloud.nativ.flamewars;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonPointer;
import javax.json.JsonString;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class OpenWeatherMapConnector {

    private static final double KELVIN_2_CELSIUS = 273.15;

    @Inject
    private OpenWeatherMapConfiguration configuration;
    private OpenWeatherMap openWeatherMap;

    @PostConstruct
    void initialize() {
        try {
            openWeatherMap = RestClientBuilder.newBuilder()
                    .baseUri(new URI(configuration.getWeatherUri()))
                    .build(OpenWeatherMap.class);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Timeout(value = 5L, unit = ChronoUnit.SECONDS)
    @Retry(delay = 500L, maxRetries = 1)
    @Fallback(fallbackMethod = "defaultWeather")
    public PayaraMicroWeather getWeather(String city) {
        JsonObject response = openWeatherMap.getWeather(city, configuration.getWeatherAppId());
        
        JsonPointer pointer;
        pointer = Json.createPointer("/weather/0/main");
        String weather = ((JsonString) pointer.getValue(response)).getString();

        pointer = Json.createPointer("/main/temp");
        double temperature = ((JsonNumber) pointer.getValue(response)).doubleValue() - KELVIN_2_CELSIUS;

        return new PayaraMicroWeather(city, weather, temperature);
    }

    public PayaraMicroWeather defaultWeather(String city) {
        return new PayaraMicroWeather(city, "Unknown", 0.0);
    }
}
