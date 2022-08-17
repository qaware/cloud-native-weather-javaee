package cloud.nativ.flamewars;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "current_weather")
@NamedQuery(name = "findCurrentWeatherByCity", query = "SELECT w FROM PayaraMicroWeather w WHERE w.city = :city AND w.nextUpdate > :now")
public class PayaraMicroWeather {

    PayaraMicroWeather() {
    }

    public PayaraMicroWeather(final String city, final String weather, final double temperature) {
        this.city = city;
        this.weather = weather;
        this.temperature = temperature;
    }

    @Id
    @Column(name = "city", unique = true, nullable = false)
    private String city;

    @Column(name = "weather", nullable = false)
    private String weather;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Column(name = "next_update", columnDefinition = "TIMESTAMP")
    @JsonbTransient
    private LocalDateTime nextUpdate;

    public String getCity() {
        return city;
    }

    public String getWeather() {
        return weather;
    }
   
    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public LocalDateTime getNextUpdate() {
        return nextUpdate;
    }

    public PayaraMicroWeather touch(int offset) {
        this.nextUpdate = LocalDateTime.now().plusHours(offset);
        return this;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }
}
