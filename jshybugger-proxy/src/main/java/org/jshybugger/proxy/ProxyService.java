package org.jshybugger.proxy;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.webbitserver.helpers.NamingThreadFactory;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * The Class ProxyService.
 */
public class ProxyService extends Service {

	/** The logging TAG. */
	private static final String TAG = "ProxyService";
	
	/** The executor services. */
	private List<ExecutorService> executorServices = new ArrayList<ExecutorService>();
    
    /** The Constant PROXY_LISTEN_PORT. */
    final private static int PROXY_LISTEN_PORT = 8080;

	/** The server bootstrap. */
	private ServerBootstrap serverBootstrap;
	
	/** The channel. */
	private Channel channel;
	
	/** The channel factory. */
	private NioClientSocketChannelFactory channelFactory;
	
	/** The is running. */
	private static boolean isRunning = false;
	
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


	/* (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		isRunning =true;
	}


	/**
	 * Start proxy server.
	 *
	 * @param remoteHost the remote host
	 * @param remotePort the remote port
	 */
	private void startProxyServer(String remoteHost, int remotePort) {
        
		if (serverBootstrap == null) {
			// Configure the bootstrap.
		    ExecutorService executor = Executors.newCachedThreadPool();
	        serverBootstrap = new ServerBootstrap();
	        
	        // Set up the event pipeline factory.
	        channelFactory = new NioClientSocketChannelFactory(executor, executor);
	        
	        serverBootstrap.setPipelineFactory(
	                new ProxyPipelineFactory(this.getApplicationContext(), channelFactory, remoteHost, remotePort));

	        ExecutorService bossExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PROXY-BOSS-THREAD"));
	        executorServices.add(bossExecutor);
	        ExecutorService workerExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PROXY-WORKER-THREAD"));
	        executorServices.add(workerExecutor);
	        serverBootstrap.setFactory(new NioServerSocketChannelFactory(bossExecutor, workerExecutor, 1));
	
	        executorServices.add(executor);
	
	        // Start up the server.
	        channel = serverBootstrap.bind(new InetSocketAddress(PROXY_LISTEN_PORT));
			LogActivity.addMessage("ProxyServer listening on port 8080");			
	        
		}
	}
	
	/* (non-Javadoc)
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (channel != null) {
            channel.close();
            channel= null;
        }
		
		if (channelFactory != null) {
			channelFactory.releaseExternalResources();
			channelFactory = null;
		}
		
		if (serverBootstrap != null) {
			serverBootstrap.releaseExternalResources();
	        serverBootstrap = null;
        }

        // shut down all services & give them a chance to terminate
        for (ExecutorService executorService : executorServices) {
            shutdownAndAwaitTermination(executorService);
        }
        
        executorServices.clear();
		isRunning = false;
		LogActivity.addMessage("ProxyServer stopped");			
	}	
	
	/* (non-Javadoc)
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "onStart: "+ intent);
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		Log.d(TAG, "onStartCommand: " + intent);
	    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
	    String remoteHost = intent != null && intent.hasExtra("host") ? intent.getStringExtra("host") :
	    		preferences.getString("host", "www.jshybugger.org");
	    
	    int remotePort = intent != null && intent.hasExtra("port") ? intent.getIntExtra("port", 80) :
	    	preferences.getInt("port", 80);
	    
	    startProxyServer(remoteHost, remotePort);
		return START_STICKY;
	}
	
    // See JavaDoc for ExecutorService
    /**
     * Shutdown and await termination.
     *
     * @param executorService the executor service
     */
    private void shutdownAndAwaitTermination(ExecutorService executorService) {
        executorService.shutdownNow(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("ExecutorService did not terminate: " + executorService);
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }	
    
    /**
     * Checks if service is running.
     *
     * @return true, if service running
     */
    public static boolean isRunning() {
    	return isRunning;
    }
}
