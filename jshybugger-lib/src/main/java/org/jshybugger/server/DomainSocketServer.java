package org.jshybugger.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;


/**
 * The class DomainSocketServer creates an jshybugger_devtools_remote unix domain socket 
 * and forwards all traffic between this socket and the HTTP endpoint 8888. 
 * Inspired from the book "Internet 
 * programming with Java" by Svetlin Nakov. It is freeware. 
 * For more information: http://www.nakov.com/books/inetjava/ 
 */
public class DomainSocketServer extends Thread {

    private static final String TAG = "DomainSocketServer";

	/** The Constant DESTINATION_HOST. */
    public static final String DESTINATION_HOST = "127.0.0.1"; 
    
	/** The thread pool. */
	private ExecutorService threadPool;

	private LocalServerSocket serverSocket;

	private int forwarPort;

	private String domainSocketName; 
 
    /**
     * Instantiates a new domain socket server.
     * @param domainSocketName Name of the domain socket
     * @throws IOException 
     */
    public DomainSocketServer(String domainSocketName, int forwarPort) throws IOException {
    	this.forwarPort = forwarPort;
    	this.domainSocketName = (domainSocketName != null ? domainSocketName : "jshybugger") + "_devtools_remote";

    	this.serverSocket = new LocalServerSocket(this.domainSocketName);			
		this.threadPool = Executors.newCachedThreadPool();
    }
    
    /**
     * Executed if thread is started.
     */
    public void run() {
		
		try {

			while (!isInterrupted()) { 
	            LocalSocket clientSocket = serverSocket.accept();
	            if (!isInterrupted()) {
	            	threadPool.execute(new ClientProcessor(clientSocket));
	            }
	        } 
			
		} catch (IOException e) {
			if (!isInterrupted()) {
				Log.e(TAG, "socket listener terminated", e);
			}
		} finally {
			try {
				if (serverSocket != null) {
					serverSocket.close();
				}
				if (threadPool != null) {
					threadPool.shutdownNow();
				}
				Log.i(TAG, "socket listener stopped");
			} catch (IOException e) {
			}
		}
    } 
    
	public void stopSocketServer() {
		if (serverSocket != null) {
			try {
				interrupt();
				LocalSocket ls = new LocalSocket();
				ls.connect(serverSocket.getLocalSocketAddress());
				ls.close();
			} catch (IOException e) {
				Log.e(TAG, "stopSocketServer failed", e);
			}
		}
	}
    
    /**
     * The Class ClientProcessor is responsible for starting forwarding between 
     * the client and the server. It keeps track of the client and 
     * servers sockets that are both closed on input/output error 
     * during the forwarding. The forwarding is bidirectional and 
     * is performed by two ForwardThread instances. 
    */
    class ClientProcessor implements Runnable { 
        
        /** The m client socket. */
        private LocalSocket mClientSocket; 
        
        /** The m server socket. */
        private Socket mServerSocket; 
        
        /** The m forwarding active. */
        private boolean mForwardingActive = false; 
     
        /**
         * Instantiates a new client processor.
         *
         * @param clientSocket the client socket
         */
        public ClientProcessor(LocalSocket clientSocket) { 
            mClientSocket = clientSocket; 
        } 
     
        /** 
         * Establishes connection to the destination server and 
         * starts bidirectional forwarding ot data between the 
         * client and the server. 
         */ 
        public void run() { 
            InputStream clientIn; 
            OutputStream clientOut; 
            InputStream serverIn; 
            OutputStream serverOut; 
            try { 
                // Connect to the destination server 
                mServerSocket = new Socket( 
                		DomainSocketServer.DESTINATION_HOST, 
                		forwarPort); 
     
                // Turn on keep-alive for both the sockets 
                mServerSocket.setKeepAlive(true); 
     
                // Obtain client & server input & output streams 
                clientIn = mClientSocket.getInputStream(); 
                clientOut = mClientSocket.getOutputStream(); 
                serverIn = mServerSocket.getInputStream(); 
                serverOut = mServerSocket.getOutputStream(); 
            } catch (IOException ioe) { 
            	Log.e(TAG, "Can not connect to " + 
                		DomainSocketServer.DESTINATION_HOST + ":" + 
                		forwarPort); 
                connectionBroken(); 
                return; 
            } 
     
            // Start forwarding data between server and client 
            mForwardingActive = true; 
            threadPool.execute(new ForwardProcessor(this, clientIn, serverOut));
            threadPool.execute(new ForwardProcessor(this, serverIn, clientOut));
        } 
     
        /** 
         * Called by some of the forwarding threads to indicate 
         * that its socket connection is brokean and both client 
         * and server sockets should be closed. Closing the client 
         * and server sockets causes all threads blocked on reading 
         * or writing to these sockets to get an exception and to 
         * finish their execution. 
         */ 
        public synchronized void connectionBroken() { 
            try { 
                mServerSocket.close(); 
            } catch (Exception e) {} 
            try { 
                mClientSocket.close(); } 
            catch (Exception e) {} 
     
            if (mForwardingActive) { 
                mForwardingActive = false; 
            } 
        } 
    }    
    
    /**
     * The Class ForwardProcessor handles the TCP forwarding between a socket 
     * input stream (source) and a socket output stream (dest). 
     * It reads the input stream and forwards everything to the 
     * output stream. If some of the streams fails, the forwarding 
     * stops and the parent is notified to close all its sockets. 
     */
    class ForwardProcessor implements Runnable { 
        
        /** The Constant BUFFER_SIZE. */
        private static final int BUFFER_SIZE = 8192; 
     
        /** The m input stream. */
        InputStream mInputStream; 
        
        /** The m output stream. */
        OutputStream mOutputStream; 
        
        /** The m parent. */
        ClientProcessor mParent; 
     
        /**
         * Creates a new traffic redirection thread specifying
         * its parent, input stream and output stream.
         *
         * @param aParent the a parent
         * @param aInputStream the a input stream
         * @param aOutputStream the a output stream
         */ 
        public ForwardProcessor(ClientProcessor aParent, InputStream 
                aInputStream, OutputStream aOutputStream) { 
            mParent = aParent; 
            mInputStream = aInputStream; 
            mOutputStream = aOutputStream; 
        } 
     
        /** 
         * Runs the thread. Continuously reads the input stream and 
         * writes the read data to the output stream. If reading or 
         * writing fail, exits the thread and notifies the parent 
         * about the failure. 
         */ 
        public void run() { 
            byte[] buffer = new byte[BUFFER_SIZE]; 
            try { 
                while (true) { 
                    int bytesRead = mInputStream.read(buffer); 
                    if (bytesRead == -1) 
                        break; // End of stream is reached --> exit 
                    mOutputStream.write(buffer, 0, bytesRead); 
                    mOutputStream.flush(); 
                } 
            } catch (IOException e) { 
                // Read/write failed --> connection is broken 
            } 
     
            // Notify parent thread that the connection is broken 
            mParent.connectionBroken(); 
        } 
    }

}
