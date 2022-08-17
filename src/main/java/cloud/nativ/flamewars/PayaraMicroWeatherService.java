package cloud.nativ.flamewars;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class PayaraMicroWeatherService {

    @Inject
    private PayaraMicroWeatherRepository repository;

    @Inject
    @ConfigProperty(name = "next.update.offset", defaultValue = "1")
    private int nextUpdateOffset;

    @Inject
    private OpenWeatherMapConnector connector;

    public PayaraMicroWeather getWeatherForCity(String city) {
        Optional<PayaraMicroWeather> currentWeather = repository.findCurrentWeatherByCity(city);
        return currentWeather.orElseGet(() -> {
            PayaraMicroWeather current = connector.getWeather(city);
            return repository.save(current.touch(nextUpdateOffset));
        });
    }
}
