package kr.itsdev.devjobcollector.jobfile;

import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/** Validates the actual resolved addresses returned to the HTTP connector. */
final class PublicAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
    @Override protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        var delegate = DefaultAddressResolverGroup.INSTANCE.getResolver(executor);
        return new AddressResolver<>() {
            public boolean isSupported(SocketAddress address) { return delegate.isSupported(address); }
            // Always route even pre-resolved addresses through the same address validation.
            public boolean isResolved(SocketAddress address) { return false; }
            public Future<InetSocketAddress> resolve(SocketAddress address) { return resolve(address, executor.newPromise()); }
            public Future<InetSocketAddress> resolve(SocketAddress address, Promise<InetSocketAddress> promise) {
                resolveAll(address).addListener(result -> {
                    if (!result.isSuccess()) promise.tryFailure(result.cause());
                    else {
                        @SuppressWarnings("unchecked") var addresses = (List<InetSocketAddress>) result.getNow();
                        if (addresses.isEmpty()) promise.tryFailure(new IllegalArgumentException("No public address"));
                        else promise.trySuccess(addresses.getFirst());
                    }
                });
                return promise;
            }
            public Future<List<InetSocketAddress>> resolveAll(SocketAddress address) { return resolveAll(address, executor.newPromise()); }
            public Future<List<InetSocketAddress>> resolveAll(SocketAddress address, Promise<List<InetSocketAddress>> promise) {
                delegate.resolveAll(address).addListener(result -> {
                    if (!result.isSuccess()) { promise.tryFailure(result.cause()); return; }
                    @SuppressWarnings("unchecked") var addresses = (List<InetSocketAddress>) result.getNow();
                    if (addresses.isEmpty() || addresses.stream().anyMatch(a -> a.isUnresolved() || !PreviewSourcePolicy.publicAddress(a.getAddress())))
                        promise.tryFailure(new IllegalArgumentException("Non-public source address"));
                    else promise.trySuccess(addresses);
                });
                return promise;
            }
            public void close() { /* The delegate is shared by Netty. */ }
        };
    }
}
