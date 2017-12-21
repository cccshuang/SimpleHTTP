package com.nwpu.cjs;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.*;

/**
 * Class <em>RequestProcessor</em> is a class handling client's request for each
 * connection.
 *
 * @version 1.0
 * @author shuang
 */

public class RequestProcessor implements Runnable {

	/**
	 * 记录日志
	 */
	private static final Logger logger = Logger
			.getLogger(RequestProcessor.class.getCanonicalName());

	/**
	 * PUT请求时服务器保存上传文件的路径
	 */
	private static final String SAVE_DIR_FOR_PUT = "C:\\Users\\S\\Desktop\\WebSrc\\put\\";

	/**
	 * String to represent the Carriage Return and Line Feed character sequence.
	 */
	private static String CRLF = "\r\n";

	/**
	 * 资源根目录
	 */
	private File rootDirectory;

	/**
	 * 主页
	 */
	private String indexFileName = "index.html";

	/**
	 * 接收到的某个客户端的连接
	 */
	private Socket connection;

	/**
	 * 构造函数
	 * 
	 * @param rootDirectory
	 *            资源根目录
	 * @param indexFileName
	 *            主页
	 * @param connection
	 *            接收到的某个客户端的连接
	 */
	public RequestProcessor(File rootDirectory, String indexFileName,
			Socket connection) {

		if (rootDirectory.isFile()) {
			throw new IllegalArgumentException("根目录必须为一个文件夹");
		}
		try {
			rootDirectory = rootDirectory.getCanonicalFile();
		} catch (IOException ex) {
		}
		this.rootDirectory = rootDirectory;

		if (indexFileName != null)
			this.indexFileName = indexFileName;
		this.connection = connection;
	}

	@Override
	public void run() {

		try {
			/**
			 * 获取Socket连接输出流的字节流
			 */
			OutputStream raw = new BufferedOutputStream(
					connection.getOutputStream());

			/**
			 * 把Socket连接输出流的字节流封装为字符流
			 */
			Writer out = new OutputStreamWriter(raw);

			/**
			 * 获取Socket连接输入流的字节流
			 */
			BufferedInputStream in = new BufferedInputStream(
					connection.getInputStream());

			/**
			 * 请求行
			 */
			StringBuilder requestLine = new StringBuilder();
			int ch = 0;
			while ((ch = in.read()) != -1) {
				requestLine.append((char) ch);
				if (ch == '\n')
					break;
			}
			String requestStr = requestLine.toString().trim();
			/**
			 * URL解码
			 */
			requestStr = URLDecoder.decode(requestStr,"utf-8");     
			logger.info(connection.getRemoteSocketAddress() + " " + requestStr);


			/**
			 * 一般来说，可以将请求行以空格为分隔符分成三部分：请求方法，请求资源，请求协议
			 */
			String[] tokens = requestStr.split(" ");

			/**
			 * 请求方法
			 */
			String method = tokens[0].toUpperCase().trim();

			/**
			 * 请求资源
			 */
			String fileName = "";
			if (tokens.length > 1) {
				fileName = tokens[1];
			}

			/**
			 * 请求协议
			 */
			String version = "";
			if (tokens.length > 2) {
				version = tokens[2];
				version = version.toUpperCase();
			}

			if (method.equals("GET")) {
				/**
				 * 当请求方法为GET时进行相应的处理
				 */
				processGetRequest(raw, out, fileName, version);
			} else if (method.equals("PUT")) {
				/**
				 * 头部信息
				 */
				StringBuilder headerLines = new StringBuilder();
				int last = 0, c = 0;
				boolean inHeader = true; // loop control
				while (inHeader && ((c = in.read()) != -1)) {
					switch (c) {
					case '\r':
						break;
					case '\n':
						if (c == last) {
							/**
							 * 已到头部信息最后的空行，将inHeader设为false，结束读取循环
							 */
							inHeader = false;
							break;
						}
						last = c;
						headerLines.append("\n");
						break;
					default:
						last = c;
						headerLines.append((char) c);
					}
				}
				String headerStr = headerLines.toString().trim();
				
				/**
				 * 当请求方法为PUT时进行相应的处理
				 */
				processPutRequest(in, out, headerStr, fileName, version);
			} else {
				/**
				 * 若是GET和PUT之外的请求,发往客户端的HTML页面
				 */
				String unImplementsBody = getHTMLResponse("Not Implemented",
						"HTTP Error 501: Not Implemented");

				if (version.startsWith("HTTP/1.0")) {
					sendHeader(out, "HTTP/1.0 501 Not Implemented",
							"text/html; charset=utf-8",
							unImplementsBody.length());
					out.write(unImplementsBody);
					out.flush();
				} else if (version.startsWith("HTTP/1.1")) {
					sendHeader(out, "HTTP/1.1 501 Not Implemented",
							"text/html; charset=utf-8",
							unImplementsBody.length());
					out.write(unImplementsBody);
					out.flush();
				}else {
					String unSupportedBody = getHTMLResponse(
							"HTTP Version Not Supported",
							"HTTP Error 505: HTTP Version Not Supported");
					sendHeader(out, "HTTP/1.0 505 HTTP Version Not Supported",
							"text/html; charset=utf-8", unSupportedBody.length());
					out.write(unSupportedBody);
					out.flush();
				}

			}
		} catch (IOException ex) {
			if(ex.getMessage().contains("Connection reset by peer: socket write error")){
				logger.info("通信过程中可能因为客户端被阻塞导致服务器暂时无法写入数据");
			}else if(ex.getMessage().contains("Software caused connection abort: socket write error")){
				logger.info("与客户端" + connection.getRemoteSocketAddress()+ "的连接异常断开");
			}else{
				logger.log(Level.WARNING, "与" + connection.getRemoteSocketAddress()
						+ "通信过程中发生错误", ex);
			}
	
		} finally {
			try {
				connection.close();
			} catch (IOException ex) {
			}
		}
	}

	/**
	 * 处理PUT请求的具体操作
	 * 
	 * @param in
	 *            接收客户端内容的字节流
	 * @param out
	 *            发送到客户端的字符流
	 * @param headerStr
	 *            请求报文的头部信息
	 * @param fileName
	 *            要上传的资源名
	 * @param version
	 *            协议版本
	 * @throws IOException
	 *             发送到客户端的流或接收客户端信息的流抛出的异常
	 */
	private void processPutRequest(BufferedInputStream in, Writer out,
			String headerStr, String fileName, String version)
			throws IOException {

		if (fileName.contains("/")) {
			/**
			 * fileName的值如"/xxx"形式时，将其值去掉"/",并设为服务器上的绝对路径
			 */
			fileName = SAVE_DIR_FOR_PUT
					+ fileName.substring(fileName.lastIndexOf("/") + 1,
							fileName.length());
		} else {
			/**
			 * fileName的值设为服务器上的绝对路径
			 */
			fileName = SAVE_DIR_FOR_PUT + fileName;
		}

		/**
		 * 当前服务其中是否有该资源
		 */
		boolean isResourceExist = false;
		File file = new File(fileName);
		if (file.exists()) {
			/**
			 * 如果当前目录存在同名文件，则删掉同名文件
			 */
			file.delete();
			isResourceExist = true;
		}

		/**
		 * 获取文件输出流，以便将内容区的值写入服务器本地文件
		 */
		try (FileOutputStream fout = new FileOutputStream(fileName, true);) {
			/**
			 * 头部消息中的Content-Length字段的值，即要读取的内容区的字节数
			 */
			int contentLen = Integer.parseInt(headerStr.substring(
					headerStr.indexOf("Content-Length: ") + 16,
					headerStr.length()));

			/**
			 * 将读取的内容临时缓存到数组中，以便之后写入文件
			 */
			byte[] buffer = new byte[8192];

			/**
			 * 一次读取的字节数
			 */
			int bytesRead = 0;

			/**
			 * 截至当前共读取的字节数
			 */
			int totalRead = 0;

			/**
			 * 从Socket的输入流中循环读取数据
			 */
			while ((bytesRead = in.read(buffer)) != -1) {
				/**
				 * 将读取的数据写入文件
				 */
				fout.write(buffer, 0, bytesRead);
				fout.flush();

				/**
				 * 当读取超过contentLen个字节时，停止读取
				 */
				totalRead += bytesRead;
				if (totalRead >= contentLen)
					break;
			}
		} catch (IOException e) {
			logger.log(Level.WARNING, connection.getRemoteSocketAddress()
					+ "进行PUT请求时保存文件失败", e);
			/**
			 * PUT请求失败时,发往客户端的HTML页面
			 */
			String failBody = getHTMLResponse("Internal Server Error",
					"HTTP Error 500: Internal Server Error");

			/**
			 * 发送MIME头部信息
			 */
			if (version.startsWith("HTTP/1.0")) {
				sendHeader(out, "HTTP/1.0 500 Internal Server Error",
						"text/html; charset=utf-8", failBody.length());
				out.write(failBody);
				out.flush();
			} else if (version.startsWith("HTTP/1.1")) {
				sendHeader(out, "HTTP/1.1 500 Internal Server Error",
						"text/html; charset=utf-8", failBody.length());
				out.write(failBody);
				out.flush();
			} else {
				String unSupportedBody = getHTMLResponse(
						"HTTP Version Not Supported",
						"HTTP Error 505: HTTP Version Not Supported");
				sendHeader(out, "HTTP/1.0 505 HTTP Version Not Supported",
						"text/html; charset=utf-8", unSupportedBody.length());
				out.write(unSupportedBody);
				out.flush();
			}

		}

		/**
		 * PUT请求成功时,发往客户端的HTML页面
		 */
		String successBody = "";
		if (isResourceExist) {
			successBody = getHTMLResponse("Resource Created",
					"A new resource is created!");
		} else {
			successBody = getHTMLResponse("Resource Modified",
					"An existing resource is modified!");
		}

		/**
		 * 发送MIME头部信息
		 */
		if (version.startsWith("HTTP/1.0")) {
			if (isResourceExist) {
				sendHeader(out, "HTTP/1.0 200 OK", "text/html; charset=utf-8",
						successBody.length());
			} else {
				sendHeader(out, "HTTP/1.0 201 Created",
						"text/html; charset=utf-8", successBody.length());
			}
			out.write(successBody);
			out.flush();
		} else if (version.startsWith("HTTP/1.1")) {
			if (isResourceExist) {
				sendHeader(out, "HTTP/1.1 200 OK", "text/html; charset=utf-8",
						successBody.length());
			} else {
				sendHeader(out, "HTTP/1.1 201 Created",
						"text/html; charset=utf-8", successBody.length());
			}
			out.write(successBody);
			out.flush();
		} else {
			String unSupportedBody = getHTMLResponse(
					"HTTP Version Not Supported",
					"HTTP Error 505: HTTP Version Not Supported");
			sendHeader(out, "HTTP/1.0 505 HTTP Version Not Supported",
					"text/html; charset=utf-8", unSupportedBody.length());
			out.write(unSupportedBody);
			out.flush();
		}

	}

	/**
	 * 处理GET请求的具体操作
	 * 
	 * @param raw
	 *            发送到客户端的字节流
	 * @param out
	 *            发送到客户端的字符流
	 * @param fileName
	 *            请求的资源名
	 * @param version
	 *            协议版本
	 * @throws IOException
	 *             送到客户端的流抛出的异常
	 */
	private void processGetRequest(OutputStream raw, Writer out,
			String fileName, String version) throws IOException {

		/**
		 * 若不指定访问的资源，则默认为主页
		 */
		if (fileName.equals("/"))
			fileName += indexFileName;		
		

		/**
		 * 获得资源的实例
		 */
		File theFile = null;
		if (fileName.startsWith("/")){
			theFile = new File(rootDirectory, fileName.substring(1,
					fileName.length()));
		}else{
			theFile = new File(rootDirectory, fileName);
		}
		


		/**
		 * 如果资源存在且可读
		 */
		if (theFile != null && theFile.exists() && theFile.canRead()) {
			/**
			 * 获得资源的全部内容，缓存到字节数组中，以便发送到客户端
			 */
			byte[] theData = Files.readAllBytes(theFile.toPath());
			
			/**
			 * 获得资源对应的MIME类型
			 */
			String contentType = URLConnection.getFileNameMap().getContentTypeFor(
					theFile.getName());
			
			if (version.startsWith("HTTP/1.0")) {
				sendHeader(out, "HTTP/1.0 200 OK", contentType, theData.length);
				raw.write(theData);
				raw.flush();
			} else if (version.startsWith("HTTP/1.1")) {
				sendHeader(out, "HTTP/1.1 200 OK", contentType, theData.length);
				raw.write(theData);
				raw.flush();
			} else {
				String unSupportedBody = getHTMLResponse(
						"HTTP Version Not Supported",
						"HTTP Error 505: HTTP Version Not Supported");
				sendHeader(out, "HTTP/1.0 505 HTTP Version Not Supported",
						"text/html; charset=utf-8", unSupportedBody.length());
				out.write(unSupportedBody);
				out.flush();
			}

		} else {
			/**
			 * 找不到资源时发送的HTML页面
			 */
			String notFoundBody = getHTMLResponse("File Not Found",
					"HTTP Error 404: File Not Found");

			if (version.startsWith("HTTP/1.0")) {
				sendHeader(out, "HTTP/1.0 404 File Not Found",
						"text/html; charset=utf-8", notFoundBody.length());
				out.write(notFoundBody);
				out.flush();
			} else if (version.startsWith("HTTP/1.1")) {
				sendHeader(out, "HTTP/1.1 404 File Not Found",
						"text/html; charset=utf-8", notFoundBody.length());
				out.write(notFoundBody);
				out.flush();
			} else {
				String unSupportedBody = getHTMLResponse(
						"HTTP Version Not Supported",
						"HTTP Error 505: HTTP Version Not Supported");
				sendHeader(out, "HTTP/1.0 505 HTTP Version Not Supported",
						"text/html; charset=utf-8", unSupportedBody.length());
				out.write(unSupportedBody);
				out.flush();
			}

		}

	}

	/**
	 * 构造发送到客户端的简单HTML页面
	 * 
	 * @param title
	 *            网页标题
	 * @param body
	 *            网页简单内容
	 * @return 简单HTML页面代码的字符串
	 */
	private String getHTMLResponse(String title, String body) {
		String htmlStr = new StringBuilder("<HTML>\r\n").append("<HEAD>")
				.append(title).append("</TITLE>\r\n").append("</HEAD>\r\n")
				.append("<BODY>").append("<H1>").append(body)
				.append("</H1>\r\n").append("</BODY></HTML>\r\n").toString();
		return htmlStr;
	}

	/**
	 * 构造并发送响应报文头部信息
	 * 
	 * @param out
	 *            发送到客户端的输出字符流
	 * @param responseCode
	 *            响应码
	 * @param contentType
	 *            MIME类型
	 * @param length
	 *            发送的内容长度
	 * @throws IOException
	 *             发送到客户端的输出字符流抛出的异常
	 */
	private void sendHeader(Writer out, String responseCode,
			String contentType, int length) throws IOException {
		out.write(responseCode + CRLF);
		Date now = new Date();
		out.write("Date: " + now + CRLF);
		out.write("Server: MyHTTPServer/1.0" + CRLF);
		out.write("Content-type: " + contentType + CRLF);
		out.write("Content-length: " + length + CRLF + CRLF);
		out.flush();
	}

}