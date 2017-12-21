package com.nwpu.cjs;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Class <em>HttpServer</em> is a class representing a simple HTTP server.
 *
 * @version 1.0
 * @author shuang
 */

public class HttpServer {

	/**
	 * 记录日志
	 */
	private static final Logger logger = Logger.getLogger(HttpServer.class
			.getCanonicalName());

	/**
	 * 单个处理器线程池工作线程数目
	 */
	private static final int POOL_SIZE = 20;

	/**
	 * 主页
	 */
	private static final String INDEX_FILE = "index.html";

	/**
	 * 资源根目录
	 */
	private final File rootDirectory;

	/**
	 * 服务器端口号，默认为80端口
	 */
	private final int port;

	/**
	 * 构造函数
	 * 
	 * @param rootDirectory
	 *            根目录参数
	 * @param port
	 *            端口号
	 * @throws IOException
	 *             传入的根目录参数不是目录时抛出异常
	 */
	public HttpServer(File rootDirectory, int port) throws IOException {
		if (!rootDirectory.isDirectory()) {
			throw new IOException(rootDirectory
					+ " does not exist as a directory");
		}
		this.rootDirectory = rootDirectory;
		this.port = port;
	}

	/**
	 * 开启服务器的函数
	 * 
	 * @throws IOException
	 *             Socket创建和连接出错抛出异常
	 */
	public void start() throws IOException {
		/**
		 * 创建一个线程池
		 */
		ExecutorService pool = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors() * POOL_SIZE);

		/**
		 * 在port端口开启ServerSocket
		 */
		try (ServerSocket server = new ServerSocket(port)) {
			logger.info("接受连接的端口： " + server.getLocalPort());
			logger.info("根目录 : " + rootDirectory);

			while (true) {
				try {
					/**
					 * 持续监听收到的Socket连接
					 */
					Socket request = server.accept();

					/**
					 * 对每个收到的连接实例化一个处理对象
					 */
					Runnable r = new RequestProcessor(rootDirectory,
							INDEX_FILE, request);

					/**
					 * 将收到的连接提交到线程池
					 */
					pool.submit(r);
				} catch (IOException ex) {
					logger.log(Level.WARNING, "接受连接出错", ex);
				}
			}
		}
	}

	public static void main(String[] args) {

		File docroot;
		try {
			/**
			 * 设置资源根目录
			 */
			docroot = new File(args[0]);
		} catch (ArrayIndexOutOfBoundsException ex) {
			/**
			 * 运行时缺少必要的参数，中断程序的运行
			 */
			System.out.println("Usage: java HttpServer docroot port");
			return;
		}

		int port;
		try {
			/**
			 * 设置监听端口，默认为80
			 */
			port = Integer.parseInt(args[1]);
			if (port < 0 || port > 65535) {
				port = 80;
			}
		} catch (RuntimeException ex) {
			port = 80;
		}

		try {
			/**
			 * 实例化一个HttpServer对象
			 */
			HttpServer webserver = new HttpServer(docroot, port);

			/**
			 * 启动服务器
			 */
			webserver.start();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "服务器启动失败", ex);
		}
	}
}
