package org.jboss.netty.channel.socket.nio;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;

/**
 * Implements the 'Builder' design pattern to allow for more complex initialisation
 * of this NIO based channel factory. In particular, concrete subclasses of this
 * builder allow custom NIO provider (SPI) implementations to be used.
 *
 * <p>
 * Generic documentation for subclasses:
 *
 * <p>
 * To obtain a channel factory via this builder use the provided constructor with its
 * mandatory argument(s) and then use the provided public methods in order to manually
 * specify the exact configuration of the factory. Finally, invoke {@link #build()} in
 * order to obtain a channel factory that is ready to use.
 *
 * <p>
 * Builder instances can be reused and reconfigured between invocations of
 * {@link #build()}, each factory returned having a separate NIO implementation context.
 *
 * <p>
 * Instances of this class are <em>not thread safe</em> and should be synchronized externally
 * if shared between threads.
 */
abstract class NioChannelFactoryBuilder<F, B extends NioChannelFactoryBuilder<F, B>> {
    // implements the GenericFactoryAbstractBuilder design pattern ;)

    // mandatory:
    final Executor workerExecutor;
    // optional with defaults:
    private int workerCount = SelectorUtil.DEFAULT_IO_THREADS;
    private SelectorProvider provider = SelectorProvider.provider();
    private NioProviderMetadata.ConstraintSpec spec = NioProviderMetadata.ConstraintSpec.NONE;

    // constructor documented in child classes
    NioChannelFactoryBuilder (Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    /**
     * Returns an NIO based {@link ChannelFactory} ready to be used for the creation
     * of channels.
     * <p>
     * Each new factory returned by this method is independent and and unrelated to
     * any previous factory.
     *
     * @return the configured NIO based channel factory instance
     */
    public abstract F build();

    /**
     * works around type system not knowing that {@code this} is the same as generic type
     * {@code B}, simply implement in child class to return child's {@code this} (child
     * instance reference).
     */
    abstract B getThis();

    // public setter methods for optional parameters:
    /**
     * Sets the maximum number of I/O worker threads, if unset this will default to
     * 2 * the number of available processors in the machine. The number of available
     * processors is obtained by {@link Runtime#availableProcessors()}.
     *
     * @param workerCount
     *        the maximum number of I/O worker threads
     *
     * @return
     *        reference to {@code this} instance (for invocation chaining)
     */
    public B workerCount(int workerCount)
        { this.workerCount = workerCount;  return getThis(); }
    /**
     * Sets the NIO {@link SelectorProvider} implementation that the factory will use,
     * if unset this defaults to the JVM wide provider found by
     * {@link SelectorProvider#provider()}.
     *
     * @param provider
     *        the NIO {@code SelectorProvider} implementation to use
     * @return
     *        reference to {@code this} instance (for invocation chaining)
     */
    public B provider(SelectorProvider provider)
        { this.provider = provider;  return getThis(); }
    /**
     * Sets the desired approach for determining the NIO provider's selector constraint
     * level. This may be an explicit 'forcing' of the constraint level, some form of
     * automatic determination or to use defaults.
     * <p>
     * See {@link NioProviderMetadata.ConstraintSpec} for more details.
     *
     * @param spec
     *        the desired constraint level specification for this factory
     * @return
     *        reference to {@code this} instance (for invocation chaining)
     */
    public B constraintSpec(NioProviderMetadata.ConstraintSpec spec)
        { this.spec = spec;  return getThis(); }

    // getter methods for target factory's constructor use of private non-final vars:
    int getWorkerCount()
        { return workerCount; }
    SelectorProvider getProvider()
        { return provider; }
    NioProviderMetadata.ConstraintSpec getConstraintSpec()
        { return spec; }
}
