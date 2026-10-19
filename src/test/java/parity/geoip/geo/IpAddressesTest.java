package parity.geoip.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IpAddressesTest {

    @Test
    void parsesIpv4() {
        assertThat(IpAddresses.parse("81.2.69.142")).isInstanceOf(Inet4Address.class)
                .extracting(a -> a.getHostAddress()).isEqualTo("81.2.69.142");
        assertThat(IpAddresses.parse("0.0.0.0").getHostAddress()).isEqualTo("0.0.0.0");
        assertThat(IpAddresses.parse("255.255.255.255").getHostAddress()).isEqualTo("255.255.255.255");
    }

    @Test
    void parsesIpv6() {
        assertThat(IpAddresses.parse("2001:218::1")).isInstanceOf(Inet6Address.class)
                .extracting(a -> a.getHostAddress()).isEqualTo("2001:218:0:0:0:0:0:1");
        assertThat(IpAddresses.parse("::1").getHostAddress()).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(IpAddresses.parse("2001:DB8::A").getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:a");
    }

    @Test
    void ipv4MappedIpv6BecomesIpv4() {
        assertThat(IpAddresses.parse("::ffff:81.2.69.142")).isInstanceOf(Inet4Address.class)
                .extracting(a -> a.getHostAddress()).isEqualTo("81.2.69.142");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", " ", "localhost", "example.com", "ff.example.com",
            "256.1.1.1", "1.2.3", "127.1", "1", "010.0.0.1", "0x7f.0.0.1", "1.2.3.4.5", "1.2.3.4:80",
            " 1.2.3.4", "1.2.3.4 ",
            "[::1]", "fe80::1%eth0", "fe80::1%1", "dead:beef", "1:2:3:4:5:6:7:8:9", ":::", "12345::", "::g"})
    void rejectsAnythingElse(String text) {
        assertThatThrownBy(() -> IpAddresses.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not an IP address: " + text);
    }
}
