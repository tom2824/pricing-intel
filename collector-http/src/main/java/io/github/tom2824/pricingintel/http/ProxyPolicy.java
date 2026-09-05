package io.github.tom2824.pricingintel.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comment sortir sur le réseau. Par défaut sans proxy : à l'échelle d'une veille quotidienne sur quelques
 * dizaines de produits, aucun site ne bloque. Le proxy existe pour les cas où l'outil tourne depuis un réseau
 * d'entreprise, ou pour un usage à plus grande échelle assumé.
 */
public sealed interface ProxyPolicy {

    ProxySelector selector();

    static ProxyPolicy none() {
        return new None();
    }

    static ProxyPolicy fixed(String host, int port) {
        return new Fixed(host, port);
    }

    /** @param hostPorts adresses au format {@code host:port} */
    static ProxyPolicy rotating(List<String> hostPorts) {
        return new Rotating(hostPorts.stream().map(ProxyPolicy::parse).toList());
    }

    private static InetSocketAddress parse(String hostPort) {
        int colon = hostPort.lastIndexOf(':');
        if (colon <= 0 || colon == hostPort.length() - 1) {
            throw new IllegalArgumentException("Expected host:port, got '" + hostPort + "'");
        }
        return InetSocketAddress.createUnresolved(hostPort.substring(0, colon), Integer.parseInt(hostPort.substring(colon + 1)));
    }

    record None() implements ProxyPolicy {
        @Override
        public ProxySelector selector() {
            return HttpClient.Builder.NO_PROXY;
        }
    }

    record Fixed(String host, int port) implements ProxyPolicy {
        public Fixed {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("proxy host is required");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("proxy port must be within 1..65535, got " + port);
            }
        }

        @Override
        public ProxySelector selector() {
            return ProxySelector.of(InetSocketAddress.createUnresolved(host, port));
        }
    }

    /** Tourne sur une liste de proxys, une adresse par requête (round-robin). */
    final class Rotating implements ProxyPolicy {
        private static final Logger LOG = LoggerFactory.getLogger(Rotating.class);

        private final List<InetSocketAddress> proxies;
        private final AtomicInteger next = new AtomicInteger();

        Rotating(List<InetSocketAddress> proxies) {
            if (proxies.isEmpty()) {
                throw new IllegalArgumentException("At least one proxy is required for a rotating policy");
            }
            this.proxies = List.copyOf(proxies);
        }

        public List<InetSocketAddress> proxies() {
            return proxies;
        }

        @Override
        public ProxySelector selector() {
            return new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    int index = Math.floorMod(next.getAndIncrement(), proxies.size());
                    return List.of(new Proxy(Proxy.Type.HTTP, proxies.get(index)));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress address, IOException e) {
                    LOG.warn("Proxy {} failed for {}: {}", address, uri.getHost(), e.getMessage());
                }
            };
        }
    }
}
