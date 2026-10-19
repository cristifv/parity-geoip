# parity-geoip: IP location, ASN and nearest zone

parity-geoip looks up an IP address in MaxMind's databases. It returns the country, region,
city and coordinates, the ASN and its organization, and the configured zones ranked by
distance (the nearest zone first).

Its place in the platform is the GeoIP service of
`../contact-center-architecture/ARCHITECTURE.md` §3.3. Requirements, decisions and open
questions are in `PROGRESS.md`.

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Kafka, Bean Validation) |
| Build | Maven |
| Boilerplate | Lombok |
| Lookups | MaxMind GeoIP2 Java 5.2 (`com.maxmind.geoip2:geoip2`), databases memory-mapped |
| Data | MaxMind GeoLite2 City (required) and GeoLite2 ASN (optional), `.mmdb` files |
| Public API | Kafka (Redpanda) |

## Code

| Package | Classes | Does |
|---|---|---|
| `parity.geoip` | `ParityGeoipApplication` | Spring Boot entry point |
| `parity.geoip.config` | `GeoipProperties` | Settings (`parity-geoip.*`): topics, database files, zones, lifecycle |
| | `ParityGeoipConfig` | The Kafka retry policy |
| `parity.geoip.api` | `RequestListener` | `@KafkaListener` on the requests topic. Writes each response, then the offset is committed |
| | `RequestHandler` | Parses and validates a request, runs it, builds the response |
| | `GeoRequest`, `Response`, `ApiException` | The request (validation groups per command), the response, client errors |
| `parity.geoip.geo` | `GeoDatabases` | Opens the City and ASN databases (memory-mapped) and checks each is of the right type |
| | `GeoLookupService` | A lookup: City, then ASN, then the zones ranked by distance |
| | `NearestZones`, `ZoneDistance` | Haversine distance from the location to each zone |
| | `IpAddresses` | Parses IP literals. Never resolves a host name |
| | `GeoLocation` | A lookup result in JSON |
| `parity.geoip.lifecycle` | `GracefulShutdown` | The healthy and drained files, and the drain on `SIGTERM` |

## Run

```sh
docker run -d --name parity-geoip-redpanda -p 19092:19092 \
  docker.redpanda.com/redpandadata/redpanda:latest redpanda start --mode dev-container \
  --smp 1 --kafka-addr internal://0.0.0.0:9092,external://0.0.0.0:19092 \
  --advertise-kafka-addr internal://localhost:9092,external://localhost:19092
docker exec parity-geoip-redpanda rpk topic create geoip.requests geoip.responses

mvn package
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:19092 java -jar target/parity-geoip.jar \
  --parity-geoip.zones[0].name=eu1 --parity-geoip.zones[0].lat=50.11 --parity-geoip.zones[0].lon=8.68 \
  --parity-geoip.zones[1].name=us1 --parity-geoip.zones[1].lat=39.04 --parity-geoip.zones[1].lon=-77.49

echo '{"id": "1", "cmd": "lookup", "ip": "193.0.6.139"}' \
  | docker exec -i parity-geoip-redpanda rpk topic produce geoip.requests
docker exec parity-geoip-redpanda rpk topic consume geoip.responses -n 1 -f '%v\n'
```

Settings are in `src/main/resources/application.yml`. Any of them can be overridden with
an environment variable or a `--name=value` argument (Spring Boot's relaxed binding):

| Setting | Environment variable | Default |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.consumer.group-id` | `SPRING_KAFKA_CONSUMER_GROUP_ID` | `parity-geoip` |
| `parity-geoip.topics.requests` | `PARITY_GEOIP_TOPICS_REQUESTS` | `geoip.requests` |
| `parity-geoip.topics.responses` | `PARITY_GEOIP_TOPICS_RESPONSES` | `geoip.responses` |
| `parity-geoip.databases.city` | `PARITY_GEOIP_DATABASES_CITY` | `/usr/share/GeoIP/GeoLite2-City.mmdb` |
| `parity-geoip.databases.asn` | `PARITY_GEOIP_DATABASES_ASN` | empty: no ASN database |
| `parity-geoip.zones[i].name` / `lat` / `lon` | `PARITY_GEOIP_ZONES_0_NAME` / `_LAT` / `_LON` | no zones |
| `parity-geoip.lifecycle.healthy-file` | `PARITY_GEOIP_LIFECYCLE_HEALTHY_FILE` | `/tmp/parity-geoip.healthy` |
| `parity-geoip.lifecycle.drained-file` | `PARITY_GEOIP_LIFECYCLE_DRAINED_FILE` | `/tmp/parity-geoip.drained` |
| `parity-geoip.lifecycle.drain-timeout` | `PARITY_GEOIP_LIFECYCLE_DRAIN_TIMEOUT` | `30s` |

- **The databases:** GeoLite2 City and ASN come from MaxMind (a free account and licence key,
  then `geoipupdate`). Fedora's `geolite2-city` package installs an old City database in
  `/usr/share/GeoIP`, which is enough for development.
- **Checked at startup:** a missing database file, a file of the wrong type (an ASN database
  as `city`), a zone configured twice, or a latitude or longitude out of range stops the
  service.
- **Zones** can also be kept in their own YAML file, given with
  `--spring.config.additional-location=zones.yml`:

  ```yaml
  parity-geoip:
    zones:
      - {name: eu1, lat: 50.11, lon: 8.68}
      - {name: us1, lat: 39.04, lon: -77.49}
  ```

Topics must exist beforehand (or the broker must auto-create them). parity-geoip doesn't
create them. Neither does it create the `reply_to` topics of requests.

## Protocol

The same conventions as parity-ms and parity-rs. A request is one JSON object with a `cmd`
field. The optional `id` (any JSON value) is echoed back. The response key is the `id`. A
response is `{"id", "ok": true, "result"}` or
`{"id", "ok": false, "error": {"code", "message"}}`. It goes to the responses topic, or to
the request's `reply_to` topic.

| cmd | Fields | Result |
|---|---|---|
| `lookup` | `ip`: an IPv4 or IPv6 address | The location (below) |
| `ping` | none | `{"pong": true}` |

With MaxMind's test databases (the City and the ASN test databases hold different
networks):

```json
{"id": "1", "cmd": "lookup", "ip": "81.2.69.142"}

{"id": "1", "ok": true, "result": {"ip": "81.2.69.142", "country": "GB",
 "country_name": "United Kingdom", "region": "ENG", "region_name": "England",
 "city": "London", "lat": 51.5142, "lon": -0.0931, "accuracy_km": 10,
 "nearest_zone": "eu1",
 "zones": [{"name": "eu1", "distance_km": 635}, {"name": "us1", "distance_km": 5920}]}}

{"id": "2", "cmd": "lookup", "ip": "1.128.0.1"}

{"id": "2", "ok": true, "result": {"ip": "1.128.0.1", "asn": 1221, "as_org": "Telstra Pty Ltd"}}
```

| Field | Meaning |
|---|---|
| `ip` | The address, normalized (`::ffff:1.2.3.4` becomes `1.2.3.4`, IPv6 without `::` compression, as in `2001:218:0:0:0:0:0:1`) |
| `country`, `country_name` | ISO 3166-1 alpha-2 code, English name |
| `region`, `region_name` | The top-level subdivision: its ISO 3166-2 code without the country (`ENG`, `WA`), English name |
| `city` | English name |
| `lat`, `lon`, `accuracy_km` | The location, and the radius it is accurate to |
| `asn`, `as_org` | From the ASN database, when one is configured |
| `zones` | Every configured zone and its great-circle distance from the location, nearest first (ties by name) |
| `nearest_zone` | The first of `zones` |

- **Fields the databases have nothing for are left out.** Many networks have a country but
  no region or city. Without coordinates there is no `zones` or `nearest_zone`.
- **Distances** are haversine on a sphere of the mean Earth radius (6,371 km), rounded to
  the kilometre. Zone health isn't taken into account yet.

Error codes:
- `bad_request`: invalid JSON, a missing `cmd` or `ip`, an unknown `cmd`, or an `ip` that
  isn't an IP address. Host names are refused, and so are old IPv4 forms (`127.1`, `010.0.0.1`)
  and IPv6 zone ids (`fe80::1%eth0`);
- `not_found`: neither database has the address (private and reserved ranges, for example);
- `internal`: an unexpected failure, such as a damaged database file.

## Delivery and shutdown

- **At-least-once.** A request's offset is committed after its response is written
  (`ack-mode: record`). If the response can't be written, the request is run again every
  second until it can. Lookups are read-only, so running one twice is harmless.
- **Responses aren't held back:** `linger.ms` is 0, since each response is written on its
  own. With the Kafka 4 default of 5 ms, one partition served about 150 lookups a second;
  with 0, about 1,600 (local Redpanda, end to end through `rpk`).
- **Health and drain files** (ARCHITECTURE.md §7.3): both files are removed at startup.
  `/tmp/parity-geoip.healthy` is created once the service has started. On `SIGTERM` the
  service removes it, stops the request listener (the request in progress finishes and its
  offset is committed), and creates `/tmp/parity-geoip.drained`. If the drain timeout runs out
  first, there is no drained file.

## Tests

```sh
mvn test
```

- `IpAddressesTest`, `NearestZonesTest`: parsing addresses; distances and ranking.
- `GeoLookupServiceTest`: lookups against MaxMind's test databases
  (`src/test/resources/mmdb`, from github.com/maxmind/MaxMind-DB, MIT or Apache 2.0).
- `RequestHandlerTest`: requests, validation, responses and error handling, with the lookup
  mocked.
- `GracefulShutdownTest`: the healthy and drained files.
- `ParityGeoipIntegrationTest`: end to end over an embedded Kafka broker, with the test
  databases and two zones.
