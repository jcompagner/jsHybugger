package org.jshybugger.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import org.jshybugger.server.DebugServer;
import org.jshybugger.server.DebugSession;

public class DebugWebAppService {

	private static final DebugWebAppService  instance = new DebugWebAppService();
	
	public static synchronized DebugWebAppService startDebugWebAppService(int debugPort, ScriptSourceProvider scriptSourceProvider)  throws IOException, InterruptedException {
		if (!instance.started) {
			instance.started = true;
			instance.start(debugPort,scriptSourceProvider);
		}
		return instance;
	}
	
	public static synchronized boolean isStarted() {
		return instance.started;
	}

	private boolean started = false;
	
	private DebugWebAppService() {
	}
	
	private void start(int debugPort, final ScriptSourceProvider scriptSourceProvider)  throws IOException, InterruptedException {
		JSDInterface browserInterface = new JSDInterface();
		DebugServer debugServer = new DebugServer( debugPort );
		debugServer.addHandler("/jshybugger/.*", new JSHybuggerResourceHandler(browserInterface));
		
		DebugSession debugSession = new DebugSession() {
			public String loadScriptResourceById(String scriptUri, boolean encode) throws IOException {
				return scriptSourceProvider.loadScriptResourceById(scriptUri, encode);
			}
		};
		debugSession.setBrowserInterface(browserInterface);
		
		debugServer.exportSession(debugSession);
	}
	public static void main(String... args) throws IOException, InterruptedException {
		if (args.length != 1) {
			System.err.println("specify the start dir where the resources are on the file system");
			return;
		}
		final String startDir = args[0]; 
		startDebugWebAppService(8889,new ScriptSourceProvider() {
			
			@Override
			public String loadScriptResourceById(String scriptUri, boolean encode)
					throws IOException {
				File file = new File(startDir + scriptUri);
				if (file.exists()) {
					return getTXTFileContent(file, Charset.forName("UTF8"));
				}
				return null;
			}
		});
	}
	
	public static String getTXTFileContent(File f, Charset charset)
	{
		if (f != null /* && f.exists() */)
		{
			if (Thread.currentThread().isInterrupted())
			{
				Thread.interrupted(); // reset interrupted flag of current thread, FileChannel.read() will throw an exception for it.
			}
			FileInputStream fis = null;
			try
			{
				int length = (int)f.length();
				if (f.exists())
				{
					fis = new FileInputStream(f);
					FileChannel fc = fis.getChannel();
					ByteBuffer bb = ByteBuffer.allocate(length);
					fc.read(bb);
					bb.rewind();
					CharBuffer cb = charset.decode(bb);
					return cb.toString();
				}
			}
			catch (Exception e)
			{
				System.err.println("Error reading txt file: " + f + ", error: "+ e); //$NON-NLS-1$
			}
			finally
			{
				try {
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return null;
	}


}
