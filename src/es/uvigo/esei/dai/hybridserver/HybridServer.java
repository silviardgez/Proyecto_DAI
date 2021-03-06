package es.uvigo.esei.dai.hybridserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.ws.Endpoint;

import es.uvigo.esei.dai.hybridserver.controller.ControllerHelper;
import es.uvigo.esei.dai.hybridserver.model.dao.DAOHelper;
import es.uvigo.esei.dai.hybridserver.model.dao.DBDAOHelper;
import es.uvigo.esei.dai.hybridserver.model.dao.MapDAOHelper;
import es.uvigo.esei.dai.hybridserver.thread.ServiceThread;
import es.uvigo.esei.dai.hybridserver.ws.HybridServerConnection;
import es.uvigo.esei.dai.hybridserver.ws.ServerConfiguration;

public class HybridServer {
	private static final int SERVICE_PORT = 8888;
	private Thread serverThread;
	private boolean stop;
	private int numClients;
	private int port;
	private String dbUrl;
	private String dbUser;
	private String dbPassword;
	private String webService;
	private DAOHelper dao;
	private List<ServerConfiguration> servers = null;
	private Endpoint endPoint;
	private ExecutorService threadPool;
	private HybridServerImpl hsImpl;

	public HybridServer() {
		this.numClients = 50;
		this.port = SERVICE_PORT;
		this.dbUrl = "jdbc:mysql://localhost:3306/hstestdb";
		this.dbUser = "hsdb";
		this.dbPassword = "hsdbpass";
		this.dao = new DBDAOHelper(dbUrl, dbUser, dbPassword);
	}

	public HybridServer(Map<String, String> pages) {
		this.numClients = 50;
		this.port = SERVICE_PORT;
		this.dao = new MapDAOHelper(pages);
	}

	public HybridServer(Properties properties) {
		this.numClients = Integer.parseInt(properties.getProperty("numClients"));
		this.port = Integer.parseInt(properties.getProperty("port"));
		this.dbUrl = properties.getProperty("db.url");
		this.dbUser = properties.getProperty("db.user");
		this.dbPassword = properties.getProperty("db.password");
		this.dao = new DBDAOHelper(dbUrl, dbUser, dbPassword);
	}

	public HybridServer(Configuration configuration) throws MalformedURLException {
		this.port = configuration.getHttpPort();
		this.webService = configuration.getWebServiceURL();
		this.numClients = configuration.getNumClients();
		this.dbUrl = configuration.getDbURL();
		this.dbUser = configuration.getDbUser();
		this.dbPassword = configuration.getDbPassword();
		this.dao = new DBDAOHelper(dbUrl, dbUser, dbPassword);
		this.servers = configuration.getServers();
	}

	public int getPort() {
		return this.port;
	}

	public int getNumClients() {
		return this.numClients;
	}

	public DAOHelper getDao() {
		return this.dao;
	}

	private String getWebService() {
		return this.webService;
	}

	public List<ServerConfiguration> getServers() {
		return servers;
	}

	public void start() {
		if (getWebService() != null) {
			hsImpl = new HybridServerImpl(getDao());
			endPoint = Endpoint.publish(getWebService(), hsImpl);
		}

		this.serverThread = new Thread() {
			@Override
			public void run() {
				try (final ServerSocket serverSocket = new ServerSocket(getPort())) {

					threadPool = Executors.newFixedThreadPool(getNumClients());
					while (true) {
						try {
							Socket socket = serverSocket.accept();

							if (stop)
								break;
							ControllerHelper htmlController = new ControllerHelper(getDao(),
									new HybridServerConnection(getServers()).connection());
							threadPool.execute(new ServiceThread(socket, htmlController));

						} catch (IOException e) {
							e.printStackTrace();
						}
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		this.stop = false;
		this.serverThread.start();
	}

	public void stop() {
		this.stop = true;

		try (Socket socket = new Socket("localhost", getPort())) {
			// Esta conexión se hace, simplemente, para "despertar" el hilo
			// servidor
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			this.serverThread.join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		threadPool.shutdownNow();

		try {
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		this.serverThread = null;
		
		if (getWebService() != null) {
			this.endPoint.stop();
			// this.hsImpl.stop();
		}
	}
}
