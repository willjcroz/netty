package org.jboss.netty.channel.socket.nio;

import java.nio.channels.spi.SelectorProvider;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;

/**
 * Represents an NIO based Netty transport class that has instances that are
 * specific to a given NIO SPI implementation.
 *
 * <p>
 * The use a particular NIO SPI implementation the context of a single NIO based
 * {@link ChannelFactory} must be consistent with regards to the selector provider
 * implementation and its related selector constraint level. Transport classes can
 * query this interface in order to obtain the relevant {@link SelectorProvider}
 * information to ensure that initialisation of NIO implementation classes is
 * consistent.
 *
 * <p>
 * Typically NIO based Netty transport types that (directly or indirectly) implement
 * {@link Channel} or {@link ChannelFactory} will provide an implementation for this
 * interface at the level in the hierarchy where the class becomes NIO specific, e.g.
 * {@link NioSocketChannel} and {@link NioClientSocketChannelFactory}.
 */
public interface NioChannelEntity {

    // XXX this interface is a workaround (and certainly needs a better name anyhow!)
    // There is no NIO specific supertype that is common to NioSocketChannel,
    // NioDatagramChannel, NioClientSocketChannelFactory, NioServerSocketChannelFactory
    // and NioDatagramChannelFactory. If there was we could add these methods there

    /**
     * Returns the NIO selector provider used by this NIO entity.
     *
     * @return the {@link SelectorProvider} used by this instance.
     */
    SelectorProvider getProvider();

    /**
     * Returns the constraint level supported by this NIO entity.
     */
    int getConstraintLevel();
}
