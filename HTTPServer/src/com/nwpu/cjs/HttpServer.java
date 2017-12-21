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
	 * ��¼��־
	 */
	private static final Logger logger = Logger.getLogger(HttpServer.class
			.getCanonicalName());

	/**
	 * �����������̳߳ع����߳���Ŀ
	 */
	private static final int POOL_SIZE = 20;

	/**
	 * ��ҳ
	 */
	private static final String INDEX_FILE = "index.html";

	/**
	 * ��Դ��Ŀ¼
	 */
	private final File rootDirectory;

	/**
	 * �������˿ںţ�Ĭ��Ϊ80�˿�
	 */
	private final int port;

	/**
	 * ���캯��
	 * 
	 * @param rootDirectory
	 *            ��Ŀ¼����
	 * @param port
	 *            �˿ں�
	 * @throws IOException
	 *             ����ĸ�Ŀ¼��������Ŀ¼ʱ�׳��쳣
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
	 * �����������ĺ���
	 * 
	 * @throws IOException
	 *             Socket���������ӳ����׳��쳣
	 */
	public void start() throws IOException {
		/**
		 * ����һ���̳߳�
		 */
		ExecutorService pool = Executors.newFixedThreadPool(Runtime
				.getRuntime().availableProcessors() * POOL_SIZE);

		/**
		 * ��port�˿ڿ���ServerSocket
		 */
		try (ServerSocket server = new ServerSocket(port)) {
			logger.info("�������ӵĶ˿ڣ� " + server.getLocalPort());
			logger.info("��Ŀ¼ : " + rootDirectory);

			while (true) {
				try {
					/**
					 * ���������յ���Socket����
					 */
					Socket request = server.accept();

					/**
					 * ��ÿ���յ�������ʵ����һ���������
					 */
					Runnable r = new RequestProcessor(rootDirectory,
							INDEX_FILE, request);

					/**
					 * ���յ��������ύ���̳߳�
					 */
					pool.submit(r);
				} catch (IOException ex) {
					logger.log(Level.WARNING, "�������ӳ���", ex);
				}
			}
		}
	}

	public static void main(String[] args) {

		File docroot;
		try {
			/**
			 * ������Դ��Ŀ¼
			 */
			docroot = new File(args[0]);
		} catch (ArrayIndexOutOfBoundsException ex) {
			/**
			 * ����ʱȱ�ٱ�Ҫ�Ĳ������жϳ��������
			 */
			System.out.println("Usage: java HttpServer docroot port");
			return;
		}

		int port;
		try {
			/**
			 * ���ü����˿ڣ�Ĭ��Ϊ80
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
			 * ʵ����һ��HttpServer����
			 */
			HttpServer webserver = new HttpServer(docroot, port);

			/**
			 * ����������
			 */
			webserver.start();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "����������ʧ��", ex);
		}
	}
}
