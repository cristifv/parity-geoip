package parity.geoip.geo;

import com.maxmind.db.Metadata;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import parity.geoip.config.GeoipProperties;

/**
 * The MaxMind databases, memory-mapped: City (required) and ASN (optional). Each file is
 * checked at startup, so a missing file or a database of the wrong type stops the service.
 */
@Slf4j
@Component
public class GeoDatabases {

    private static final InetAddress PROBE = IpAddresses.parse("127.0.0.1");

    private final DatabaseReader city;
    private final DatabaseReader asn;

    public GeoDatabases(GeoipProperties properties) throws IOException {
        String asnPath = properties.databases().asn();
        city = open("city", properties.databases().city(), reader -> reader.tryCity(PROBE));
        asn = asnPath == null || asnPath.isBlank() ? null : open("asn", asnPath, reader -> reader.tryAsn(PROBE));
        if (asn == null) {
            log.info("no asn database: lookups have no asn or as_org");
        }
    }

    public DatabaseReader city() {
        return city;
    }

    public Optional<DatabaseReader> asn() {
        return Optional.ofNullable(asn);
    }

    @PreDestroy
    void close() throws IOException {
        city.close();
        if (asn != null) {
            asn.close();
        }
    }

    /** A lookup that only succeeds on the right kind of database. */
    @FunctionalInterface
    private interface Check {
        void run(DatabaseReader reader) throws IOException, GeoIp2Exception;
    }

    private static DatabaseReader open(String kind, String path, Check check) throws IOException {
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalStateException(kind + " database not found: " + path);
        }
        DatabaseReader reader = new DatabaseReader.Builder(file).fileMode(Reader.FileMode.MEMORY_MAPPED).build();
        Metadata metadata = reader.metadata();
        try {
            check.run(reader);
        } catch (UnsupportedOperationException | GeoIp2Exception e) {
            reader.close();
            throw new IllegalStateException(
                    kind + " database " + path + " is " + metadata.databaseType() + ": " + e.getMessage());
        }
        log.info("{} database {}: {}, built {}", kind, path, metadata.databaseType(), metadata.buildTime());
        return reader;
    }
}
