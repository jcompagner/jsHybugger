package org.jshybugger.proxy;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import android.content.Context;

public class ProxyPipelineFactory implements ChannelPipelineFactory {

    private final ClientSocketChannelFactory cf;
    private final String remoteHost;
    private final int remotePort;
    private final Context context;
    
    public ProxyPipelineFactory(
            Context context, ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
        this.cf = cf;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.context = context;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline(); // Note the static import.
        pipeline.addLast("httpReqDecoder", new HttpRequestDecoder());        
        pipeline.addLast("httpResEncoder", new HttpResponseEncoder());        
        pipeline.addLast("instrumentation", new DebugInstrumentationHandler(context));
        pipeline.addLast("handler", new ProxyInboundHandler(cf, remoteHost, remotePort));
        return pipeline;
    }
}
