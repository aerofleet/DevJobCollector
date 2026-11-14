package kr.itsdev.devjobcollector.jobfile;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import io.netty.channel.DefaultEventLoopGroup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PreviewSourcePolicyTest {
    @Test void rewritesOnlyTheExactLegacyAlioFileUrl() {
        assertThat(PreviewSourcePolicy.source("공고.hwp", "https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=3085641").toString())
                .isEqualTo("https://www.alio.go.kr/download/download.json?fileNo=3085641");
        for (String url : new String[] {"http://www.alio.go.kr/a", "https://www.alio.go.kr.evil.example/a", "https://www.alio.go.kr@evil.example/a", "https://www.alio.go.kr:8443/a", "https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=1&url=https://evil.example"})
            assertThatThrownBy(() -> PreviewSourcePolicy.source("공고.hwp", url)).isInstanceOf(IllegalArgumentException.class);
        for (String extension : new String[] {"pdf", "png", "jpg", "jpeg", "webp"})
            assertThat(PreviewSourcePolicy.source("공고." + extension, "https://www.alio.go.kr/a")).isNotNull();
        assertThatThrownBy(() -> PreviewSourcePolicy.source("공고.svg", "https://www.alio.go.kr/a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsNonPublicNetworkAddresses() throws Exception {
        for (String ip : new String[] {"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1"})
            assertThat(PreviewSourcePolicy.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();
        assertThat(PreviewSourcePolicy.publicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(PreviewSourcePolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isFalse();
    }

    @Test void resolverRejectsBothLiteralAndAlreadyResolvedPrivateAddresses() {
        var loop = new DefaultEventLoopGroup(1);
        var group = new PublicAddressResolverGroup();
        try {
            var resolver = group.getResolver(loop.next());
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("127.0.0.1", 443)).awaitUninterruptibly().isSuccess()).isFalse();
            assertThat(resolver.resolve(new InetSocketAddress("127.0.0.1", 443)).awaitUninterruptibly().isSuccess()).isFalse();
        } finally { group.close(); loop.shutdownGracefully().syncUninterruptibly(); }
    }

    @Test void mixedDnsAnswersOnlyReturnValidatedPublicIpv4() throws Exception {
        var publicV4 = new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 443);
        var publicV6 = new InetSocketAddress(InetAddress.getByName("2606:4700:4700::1111"), 443);
        var privateV4 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 443);
        assertThat(PublicAddressResolverGroup.publicIpv4Addresses(List.of(publicV4, publicV6))).containsExactly(publicV4);
        assertThatThrownBy(() -> PublicAddressResolverGroup.publicIpv4Addresses(List.of(publicV6))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicAddressResolverGroup.publicIpv4Addresses(List.of(publicV4, publicV6, privateV4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicAddressResolverGroup.publicIpv4Addresses(List.of(publicV4, InetSocketAddress.createUnresolved("example.com", 443)))).isInstanceOf(IllegalArgumentException.class);
    }
}
