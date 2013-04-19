package org.jshybugger.proxy;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jshybugger.instrumentation.JsCodeLoader;
import org.jshybugger.server.Md5Checksum;
import org.mozilla.javascript.EvaluatorException;

import android.content.Context;

public class DebugInstrumentationHandler extends SimpleChannelHandler {

	private static final String JS_HYBUGGER = "jsHybugger-";
	private String requestURI;
	private String requestMethod;
	private Context context;
	private static int requestId = 0;

	public DebugInstrumentationHandler(Context context) {
		this.context = context;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {

		if (!(e.getMessage() instanceof HttpMessage)) {
			ctx.sendUpstream(e);
			return;
		}

		HttpRequest msg = (HttpRequest) e.getMessage();
		requestURI = msg.getUri();
		requestMethod = msg.getMethod().getName();
		
		try {
			String if_header = msg.getHeader("If-None-Match");
			if (if_header != null && if_header.startsWith(JS_HYBUGGER)) {
				msg.setHeader("If-None-Match", if_header.substring(JS_HYBUGGER.length()));
				
				FileInputStream is = context.openFileInput(DebugInstrumentationHandler.getInstrumentedFileName(requestURI, null));
				is.close();
			}
		} catch (FileNotFoundException ex) {
			msg.removeHeader("Cache-Control"); 
			msg.removeHeader("If-None-Match"); 
			msg.removeHeader("If-Modified-Since");
		}

		ctx.sendUpstream(e);
	}

	@Override
	public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {

		int reqCtx = (++requestId);
		
		Object msg = e.getMessage();
		if (msg instanceof HttpResponse) {
			LogActivity.addMessage(String.format("R%05d %s %s, SC=%s", reqCtx, requestMethod, requestURI, ((HttpResponse) msg).getStatus().getCode()));
		}
		
		if (msg instanceof HttpResponse
				&& ((HttpResponse) msg).getStatus().getCode() == 100) {
			
			// 100-continue response must be passed through.
			ctx.sendDownstream(e);
		} else if (msg instanceof HttpResponse) {
			HttpResponse m = (HttpResponse) msg;
			String contentType = m.getHeader("Content-Type");
			
			if ((requestURI.endsWith(".html") && !requestURI.endsWith(".jsp")) ||
					((contentType != null) && contentType.contains("html"))) {
				
				LogActivity.addMessage(String.format("R%05d Injecting JSHybugger script", reqCtx));				
				String scriptTag = "<script src=\"http://localhost:8888/jshybugger/jshybugger.js\"></script>";
				int contentLength = m.getContent().readableBytes() + scriptTag.length();
				ChannelBuffer buffer = ChannelBuffers.buffer(contentLength);
				buffer.writeBytes(scriptTag.getBytes());
				buffer.writeBytes(m.getContent());
				
				m.setContent(buffer);
				m.setHeader(
		                HttpHeaders.Names.CONTENT_LENGTH,
		                Integer.toString(contentLength));		
				
			} else if ((requestURI.endsWith(".js") || ((contentType != null) && contentType.contains("javascript")))
					   && !requestURI.endsWith(".min.js")) {

				if (contentType == null) {
					m.addHeader("Content-Type", "application/javascript");
				}
				
				m.setHeader("ETag", JS_HYBUGGER + m.getHeader("ETag"));

				if (m.getStatus().getCode() == 304)  { // not-modified
		            ctx.sendDownstream(e);
		            return;
				}
				
				BufferedInputStream resStream = new BufferedInputStream(new ChannelBufferInputStream(m.getContent()));
				
				String instrumentedFileName = getInstrumentedFileName(requestURI, resStream);
				try {
					writeOriginalFile(getInstrumentedFileName(requestURI, null), resStream);
					sendInstrumentedFile(m, instrumentedFileName);
					
				} catch (FileNotFoundException fex) {

					FileOutputStream outputStream = context.openFileOutput(instrumentedFileName, Context.MODE_PRIVATE);
					
					LogActivity.addMessage(String.format("R%05d Starting script instrumentation", reqCtx));				
					try {
						resStream.reset();
						JsCodeLoader.instrumentFile(resStream, requestURI, outputStream);
						LogActivity.addMessage(String.format("R%05d Script instrumentation successfully completed", reqCtx));				
						sendInstrumentedFile(m, instrumentedFileName);

					} catch (EvaluatorException ex) {

				        // delete file - maybe partially instrumented file.
						context.deleteFile(instrumentedFileName);
						
						String writeConsole = "console.error('Javascript syntax error: " + ex.getMessage().replace("'", "\"") + "')";
						m.setContent(ChannelBuffers.wrappedBuffer(writeConsole.getBytes()));
						m.setHeader(
				                HttpHeaders.Names.CONTENT_LENGTH,
				                Integer.toString(writeConsole.length()));		
						LogActivity.addMessage(String.format("R%05d Script instrumentation failed: %s", reqCtx, writeConsole));				
						
					} catch (Exception ie) {

				        // delete file - maybe partially instrumented file.
				        context.deleteFile(instrumentedFileName);
						LogActivity.addMessage(String.format("R%05d Script instrumentation failed: %s", reqCtx, ie.toString()));				

					} finally {
						resStream.close();
					}
				}
			}
			
            ctx.sendDownstream(e);
		}
	}


	private void writeOriginalFile(String instrumentedFileName,
			BufferedInputStream resStream) throws IOException {

		FileOutputStream outputStream = context.openFileOutput(instrumentedFileName, Context.MODE_PRIVATE);
		byte buffer[] = new byte[1024];
		int numBytes =0;
		
		resStream.reset();
		while ((numBytes = resStream.read(buffer)) > 0) {
			outputStream.write(buffer, 0, numBytes);
		}
		outputStream.close();
	}

	private void sendInstrumentedFile(HttpMessage m, String instrumentedFileName) 
			throws FileNotFoundException, IOException {
		FileInputStream instrumentedFile = context.openFileInput(instrumentedFileName);
		
		int contentLength = instrumentedFile.available();
		ChannelBuffer buffer = ChannelBuffers.buffer(contentLength);
		buffer.writeBytes(instrumentedFile, contentLength);
		instrumentedFile.close();
		m.setContent(buffer);
		m.setHeader(
                HttpHeaders.Names.CONTENT_LENGTH,
                Integer.toString(contentLength));		
	}
	
	/**
	 * Gets the instrumented file name.
	 *
	 * @param url the url to instrument
	 * @param resource resource input stream
	 * @return the instrument file name
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static String getInstrumentedFileName(String url, BufferedInputStream resource) throws IOException {
		String loadUrl = null;
		try {
			loadUrl = url.replaceAll("[/:?=]", "_");
					
			if (resource != null) {
				resource.mark(1000000);
				loadUrl	+= Md5Checksum.getMD5Checksum(resource);
			}
			
		} catch (Exception e) {
			throw new IOException("getInstrumentedFileName failed:" + url, e);
		} 
		return loadUrl;
	}		
}
