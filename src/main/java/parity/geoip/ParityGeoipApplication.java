package parity.geoip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** parity-geoip: IP location, ASN and nearest zone. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ParityGeoipApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParityGeoipApplication.class, args);
    }
}
