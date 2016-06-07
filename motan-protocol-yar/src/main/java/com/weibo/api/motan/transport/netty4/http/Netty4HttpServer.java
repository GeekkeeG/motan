/*
 * Copyright 2009-2016 Weibo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.weibo.api.motan.transport.netty4.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.weibo.api.motan.common.MotanConstants;
import com.weibo.api.motan.common.URLParamType;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import com.weibo.api.motan.transport.AbstractServer;
import com.weibo.api.motan.transport.MessageHandler;
import com.weibo.api.motan.transport.TransportException;
import com.weibo.api.motan.util.LoggerUtil;
import com.weibo.api.motan.util.StatisticCallback;

/**
 * 
 * @Description netty 4 http server.
 * @author zhanglei
 * @date 2016年5月31日
 *
 */
// TODO 后续移到transport netty4 模块
public class Netty4HttpServer extends AbstractServer implements StatisticCallback {
    private MessageHandler messageHandler;
    private URL url;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public Netty4HttpServer(URL url, MessageHandler messageHandler) {
        this.url = url;
        this.messageHandler = messageHandler;
    }


    @Override
    public boolean open() {
        if (isAvailable()) {
            return true;
        }
        if (channel != null) {
            channel.close();
        }
        if (bossGroup == null) {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
        }
        boolean shareChannel = url.getBooleanParameter(URLParamType.shareChannel.getName(), URLParamType.shareChannel.getBooleanValue());
        // TODO 最大链接保护
        int maxServerConnection =
                url.getIntParameter(URLParamType.maxServerConnection.getName(), URLParamType.maxServerConnection.getIntValue());
        int workerQueueSize = url.getIntParameter(URLParamType.workerQueueSize.getName(), 500);

        int minWorkerThread = 0, maxWorkerThread = 0;

        if (shareChannel) {
            minWorkerThread = url.getIntParameter(URLParamType.minWorkerThread.getName(), MotanConstants.NETTY_SHARECHANNEL_MIN_WORKDER);
            maxWorkerThread = url.getIntParameter(URLParamType.maxWorkerThread.getName(), MotanConstants.NETTY_SHARECHANNEL_MAX_WORKDER);
        } else {
            minWorkerThread =
                    url.getIntParameter(URLParamType.minWorkerThread.getName(), MotanConstants.NETTY_NOT_SHARECHANNEL_MIN_WORKDER);
            maxWorkerThread =
                    url.getIntParameter(URLParamType.maxWorkerThread.getName(), MotanConstants.NETTY_NOT_SHARECHANNEL_MAX_WORKDER);
        }
        final NettyHttpRequestHandler handler =
                new NettyHttpRequestHandler(this, messageHandler, new ThreadPoolExecutor(minWorkerThread, maxWorkerThread, 15,
                        TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(workerQueueSize)));

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("http-decoder", new HttpRequestDecoder());
                ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65536));
                ch.pipeline().addLast("http-encoder", new HttpResponseEncoder());
                ch.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                ch.pipeline().addLast("serverHandler", handler);
            }
        }).option(ChannelOption.SO_BACKLOG, 1024).childOption(ChannelOption.SO_KEEPALIVE, false);

        ChannelFuture f;
        try {
            f = b.bind(url.getPort()).sync();
            channel = f.channel();
        } catch (InterruptedException e) {
            LoggerUtil.error("init http server fail.", e);
            return false;
        }

        return true;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            workerGroup = null;
            bossGroup = null;
        }
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean isAvailable() {
        return channel == null ? false : channel.isOpen();
    }


    @Override
    public boolean isBound() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public Response request(Request request) throws TransportException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void close(int timeout) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public boolean isClosed() {
        return !channel.isOpen();
    }



    @Override
    public String statisticCallback() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public URL getUrl() {
        return url;
    }

   

}
