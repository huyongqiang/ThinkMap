/*
 * Copyright 2014 Matthew Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.co.thinkofdeath.thinkcraft.bukkit.web;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import uk.co.thinkofdeath.thinkcraft.bukkit.ThinkMapPlugin;

public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final ThinkMapPlugin plugin;

    public ServerChannelInitializer(ThinkMapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();
        pipeline.addLast("timeout", new ReadTimeoutHandler(15));
        pipeline.addLast("codec-http", new HttpServerCodec());
        pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
        pipeline.addLast("handler", new HTTPHandler(plugin));
        pipeline.addLast("websocket", new WebSocketServerProtocolHandler("/server"));
        pipeline.addLast("packet-decoder", new PacketDecoder());
        pipeline.addLast("packet-encoder", new PacketEncoder());
        pipeline.addLast("packet-handler", new ClientHandler(socketChannel, plugin));

        socketChannel.config().setAllocator(PooledByteBufAllocator.DEFAULT);

        plugin.getWebHandler().getChannelGroup().add(socketChannel);
    }
}
