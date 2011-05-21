/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.nio;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * A {@link DatagramChannelFactory} that creates a NIO-based connectionless
 * {@link DatagramChannel}. It utilizes the non-blocking I/O mode which
 * was introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There is only one thread type in a {@link NioDatagramChannelFactory};
 * worker threads.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link NioDatagramChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link DatagramChannel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All worker threads are acquired from the {@link Executor} which was specified
 * when a {@link NioDatagramChannelFactory} was created.  Therefore, you should
 * make sure the specified {@link Executor} is able to lend the sufficient
 * number of threads.  It is the best bet to specify
 * {@linkplain Executors#newCachedThreadPool() a cached thread pool}.
 * <p>
 * All worker threads are acquired lazily, and then released when there's
 * nothing left to process.  All the related resources such as {@link Selector}
 * are also released when the worker threads are released.  Therefore, to shut
 * down a service gracefully, you should do the following:
 *
 * <ol>
 * <li>close all channels created by the factory usually using
 *     {@link ChannelGroup#close()}, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * <h3>Limitation</h3>
 * <p>
 * Multicast is not supported.  Please use {@link OioDatagramChannelFactory}
 * instead.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class NioDatagramChannelFactory
        implements DatagramChannelFactory, NioChannelEntity {

    private final Executor workerExecutor;
    private final SelectorProvider provider;
    private final int CONSTRAINT_LEVEL;
    private final NioDatagramPipelineSink sink;

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioDatagramChannelFactory(Executor, int)} with 2 * the number of
     * available processors in the machine.  The number of available processors
     * is obtained by {@link Runtime#availableProcessors()}.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     */
    public NioDatagramChannelFactory(final Executor workerExecutor) {
        this(workerExecutor, SelectorUtil.DEFAULT_IO_THREADS);
    }

    /**
     * Creates a new instance.
     *
     * This factory will use the JVM wide default NIO implementation as obtained
     * by {@link SelectorProvider#provider()}, and its default constraint level.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public NioDatagramChannelFactory(final Executor workerExecutor,
            final int workerCount) {
        this(new Builder(workerExecutor).workerCount(workerCount));
    }

    private NioDatagramChannelFactory(Builder builder) {
        int workerCount = builder.getWorkerCount();
        if (workerCount <= 0) {
            throw new IllegalArgumentException(String
                    .format("workerCount (%s) must be a positive integer.",
                            workerCount));
        }
        if (builder.workerExecutor == null) {
            throw new NullPointerException(
                    "workerExecutor argument must not be null");
        }
        this.workerExecutor = builder.workerExecutor;
        this.provider = builder.getProvider();
        this.CONSTRAINT_LEVEL = NioProviderMetadata.getConstraintLevel(provider,
                builder.getConstraintSpec());

        sink = new NioDatagramPipelineSink(workerExecutor, workerCount);
    }

    /**
     * Implements the 'Builder' design pattern to allow for more complex initialisation
     * of this NIO based channel factory. In particular, this builder allows custom NIO
     * provider (SPI) implementations to be used.
     * <p>
     * To obtain a channel factory via this builder use the provided constructor with its
     * mandatory argument(s) and then use the provided public methods in order to manually
     * specify the exact configuration of the factory. All setting methods for optional
     * configuration values return a reference to {@code this} allowing invocation chaining
     * Finally, invoke {@link #build()} in order to obtain a channel factory that is ready
     * to use.
     * <p>
     * Builder instances can be reused and reconfigured between invocations of
     * {@link #build()}, each factory returned having a separate NIO implementation context.
     * <p>
     * Example usage:
     * <p>
     * <pre><code>
     *     NioDatagramChannelFactory factory =
     *         new NioDatagramChannelFactory.Builder(workerExecutor)
     *             .provider(customNioProvider)
     *             .constraintSpec(NioProviderMetadata.ConstraintSpec.NO_WAKE)
     *             .workerCount(4)
     *             .build();
     * </code></pre>
     *<p>
     * Instances of this class are <em>not thread safe</em> and should be synchronized externally
     * if shared between threads.
     */
    public static final class Builder
            extends NioChannelFactoryBuilder<NioDatagramChannelFactory, Builder> {

        /**
         * Creates a builder instance that will return initialised and ready to use
         * {@link NioDatagramChannelFactory} instances. Before invoking build the
         * optional parameters can be changed from their default values.
         *
         * @param workerExecutor
         *        the {@link Executor} which will execute the I/O worker threads
         */
        public Builder (Executor workerExecutor) {
            super(workerExecutor);
        }

        @Override
        public NioDatagramChannelFactory build() {
            return new NioDatagramChannelFactory(this);
        }

        @Override
        Builder getThis() {
            return this;
        }
    }

    @Override
    public DatagramChannel newChannel(final ChannelPipeline pipeline) {
        return new NioDatagramChannel(this, pipeline, sink, sink.nextWorker());
    }

    @Override
    public void releaseExternalResources() {
        ExecutorUtil.terminate(workerExecutor);
    }

    @Override
    public SelectorProvider getProvider() {
        return provider;
    }

    @Override
    public int getConstraintLevel() {
        return CONSTRAINT_LEVEL;
    }
}
