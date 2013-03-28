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

public class ProxyService extends Service {

	/** The logging TAG */
	private static final String TAG = "ProxyService";
	
	private List<ExecutorService> executorServices = new ArrayList<ExecutorService>();
    final private static long STALE_CONNECTION_TIMEOUT = 5000;

	private ServerBootstrap serverBootstrap;
	private Channel channel;
	private NioClientSocketChannelFactory channelFactory;
	
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
	}


	private void startProxyServer(String remoteHost, int remotePort) {
		int localPort = 8080;
        // Configure the bootstrap.
	    ExecutorService executor = Executors.newCachedThreadPool();
        serverBootstrap = new ServerBootstrap();
        
        // Set up the event pipeline factory.
        channelFactory = new NioClientSocketChannelFactory(executor, executor);
        
        serverBootstrap.setPipelineFactory(
                new ProxyPipelineFactory(this.getApplicationContext(), channelFactory, remoteHost, remotePort));
/*
		final StaleConnectionTrackingHandler staleConnectionTrackingHandler = new StaleConnectionTrackingHandler(STALE_CONNECTION_TIMEOUT, executor);
        ScheduledExecutorService staleCheckExecutor = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory("PROXY-STALE-CONNECTION-CHECK-THREAD"));
        staleCheckExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
            	synchronized (staleConnectionTrackingHandler) {
            		staleConnectionTrackingHandler.closeStaleConnections();
            	}
            }
        }, STALE_CONNECTION_TIMEOUT / 2, STALE_CONNECTION_TIMEOUT / 2, TimeUnit.MILLISECONDS);
        executorServices.add(staleCheckExecutor);
*/
        ExecutorService bossExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PROXY-BOSS-THREAD"));
        executorServices.add(bossExecutor);
        ExecutorService workerExecutor = Executors.newSingleThreadExecutor(new NamingThreadFactory("PROXY-WORKER-THREAD"));
        executorServices.add(workerExecutor);
        serverBootstrap.setFactory(new NioServerSocketChannelFactory(bossExecutor, workerExecutor, 1));

        executorServices.add(executor);

        // Start up the server.
        channel = serverBootstrap.bind(new InetSocketAddress(localPort));
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (channel != null) {
            channel.close();
        }
		
		if (channelFactory != null) {
			channelFactory.releaseExternalResources();
		}
		
		if (serverBootstrap != null) {
			serverBootstrap.releaseExternalResources();
        }

        // shut down all services & give them a chance to terminate
        for (ExecutorService executorService : executorServices) {
            shutdownAndAwaitTermination(executorService);
        }
        
        executorServices.clear();
        serverBootstrap = null;
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
}
