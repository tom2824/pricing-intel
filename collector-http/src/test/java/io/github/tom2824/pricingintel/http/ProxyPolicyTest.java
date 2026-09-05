package io.github.tom2824.pricingintel.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProxyPolicyTest {

    private static final URI URL = URI.create("https://shop.test/");

    @Test
    void noneUsesDirectConnections() {
        List<Proxy> proxies = ProxyPolicy.none().selector().select(URL);

        assertThat(proxies).containsExactly(Proxy.NO_PROXY);
    }

    @Test
    void fixedAlwaysReturnsTheSameProxy() {
        ProxySelector selector = ProxyPolicy.fixed("proxy.corp", 3128).selector();

        assertThat(selector.select(URL)).singleElement()
                .extracting(Proxy::address)
                .isEqualTo(InetSocketAddress.createUnresolved("proxy.corp", 3128));
    }

    @Test
    void rotatingCyclesThroughProxiesInOrder() {
        ProxySelector selector = ProxyPolicy.rotating(List.of("p1:8080", "p2:8080")).selector();

        assertThat(hostPort(selector)).isEqualTo("p1:8080");
        assertThat(hostPort(selector)).isEqualTo("p2:8080");
        assertThat(hostPort(selector)).isEqualTo("p1:8080");
    }

    private static String hostPort(ProxySelector selector) {
        InetSocketAddress address = (InetSocketAddress) selector.select(URL).get(0).address();
        return address.getHostString() + ":" + address.getPort();
    }

    @Test
    void rejectsMalformedProxyAddresses() {
        assertThatThrownBy(() -> ProxyPolicy.rotating(List.of("no-port")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProxyPolicy.fixed("host", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userAgentTokenIsTheProductName() {
        assertThat(FetcherConfig.defaults().userAgentToken()).isEqualTo("pricing-intel");
        assertThat(FetcherConfig.defaults().withUserAgent("MyBot (contact@x.test)").userAgentToken()).isEqualTo("MyBot");
    }
}
