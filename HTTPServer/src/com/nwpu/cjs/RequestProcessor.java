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
	 * ��¼��־
	 */
	private static final Logger logger = Logger
			.getLogger(RequestProcessor.class.getCanonicalName());

	/**
	 * PUT����ʱ�����������ϴ��ļ���·��
	 */
	private static final String SAVE_DIR_FOR_PUT = "C:\\Users\\S\\Desktop\\WebSrc\\put\\";

	/**
	 * String to represent the Carriage Return and Line Feed character sequence.
	 */
	private static String CRLF = "\r\n";

	/**
	 * ��Դ��Ŀ¼
	 */
	private File rootDirectory;

	/**
	 * ��ҳ
	 */
	private String indexFileName = "index.html";

	/**
	 * ���յ���ĳ���ͻ��˵�����
	 */
	private Socket connection;

	/**
	 * ���캯��
	 * 
	 * @param rootDirectory
	 *            ��Դ��Ŀ¼
	 * @param indexFileName
	 *            ��ҳ
	 * @param connection
	 *            ���յ���ĳ���ͻ��˵�����
	 */
	public RequestProcessor(File rootDirectory, String indexFileName,
			Socket connection) {

		if (rootDirectory.isFile()) {
			throw new IllegalArgumentException("��Ŀ¼����Ϊһ���ļ���");
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
			 * ��ȡSocket������������ֽ���
			 */
			OutputStream raw = new BufferedOutputStream(
					connection.getOutputStream());

			/**
			 * ��Socket������������ֽ�����װΪ�ַ���
			 */
			Writer out = new OutputStreamWriter(raw);

			/**
			 * ��ȡSocket�������������ֽ���
			 */
			BufferedInputStream in = new BufferedInputStream(
					connection.getInputStream());

			/**
			 * ������
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
			 * URL����
			 */
			requestStr = URLDecoder.decode(requestStr,"utf-8");     
			logger.info(connection.getRemoteSocketAddress() + " " + requestStr);


			/**
			 * һ����˵�����Խ��������Կո�Ϊ�ָ����ֳ������֣����󷽷���������Դ������Э��
			 */
			String[] tokens = requestStr.split(" ");

			/**
			 * ���󷽷�
			 */
			String method = tokens[0].toUpperCase().trim();

			/**
			 * ������Դ
			 */
			String fileName = "";
			if (tokens.length > 1) {
				fileName = tokens[1];
			}

			/**
			 * ����Э��
			 */
			String version = "";
			if (tokens.length > 2) {
				version = tokens[2];
				version = version.toUpperCase();
			}

			if (method.equals("GET")) {
				/**
				 * �����󷽷�ΪGETʱ������Ӧ�Ĵ���
				 */
				processGetRequest(raw, out, fileName, version);
			} else if (method.equals("PUT")) {
				/**
				 * ͷ����Ϣ
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
							 * �ѵ�ͷ����Ϣ���Ŀ��У���inHeader��Ϊfalse��������ȡѭ��
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
				 * �����󷽷�ΪPUTʱ������Ӧ�Ĵ���
				 */
				processPutRequest(in, out, headerStr, fileName, version);
			} else {
				/**
				 * ����GET��PUT֮�������,�����ͻ��˵�HTMLҳ��
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
				logger.info("ͨ�Ź����п�����Ϊ�ͻ��˱��������·�������ʱ�޷�д������");
			}else if(ex.getMessage().contains("Software caused connection abort: socket write error")){
				logger.info("��ͻ���" + connection.getRemoteSocketAddress()+ "�������쳣�Ͽ�");
			}else{
				logger.log(Level.WARNING, "��" + connection.getRemoteSocketAddress()
						+ "ͨ�Ź����з�������", ex);
			}
	
		} finally {
			try {
				connection.close();
			} catch (IOException ex) {
			}
		}
	}

	/**
	 * ����PUT����ľ������
	 * 
	 * @param in
	 *            ���տͻ������ݵ��ֽ���
	 * @param out
	 *            ���͵��ͻ��˵��ַ���
	 * @param headerStr
	 *            �����ĵ�ͷ����Ϣ
	 * @param fileName
	 *            Ҫ�ϴ�����Դ��
	 * @param version
	 *            Э��汾
	 * @throws IOException
	 *             ���͵��ͻ��˵�������տͻ�����Ϣ�����׳����쳣
	 */
	private void processPutRequest(BufferedInputStream in, Writer out,
			String headerStr, String fileName, String version)
			throws IOException {

		if (fileName.contains("/")) {
			/**
			 * fileName��ֵ��"/xxx"��ʽʱ������ֵȥ��"/",����Ϊ�������ϵľ���·��
			 */
			fileName = SAVE_DIR_FOR_PUT
					+ fileName.substring(fileName.lastIndexOf("/") + 1,
							fileName.length());
		} else {
			/**
			 * fileName��ֵ��Ϊ�������ϵľ���·��
			 */
			fileName = SAVE_DIR_FOR_PUT + fileName;
		}

		/**
		 * ��ǰ���������Ƿ��и���Դ
		 */
		boolean isResourceExist = false;
		File file = new File(fileName);
		if (file.exists()) {
			/**
			 * �����ǰĿ¼����ͬ���ļ�����ɾ��ͬ���ļ�
			 */
			file.delete();
			isResourceExist = true;
		}

		/**
		 * ��ȡ�ļ���������Ա㽫��������ֵд������������ļ�
		 */
		try (FileOutputStream fout = new FileOutputStream(fileName, true);) {
			/**
			 * ͷ����Ϣ�е�Content-Length�ֶε�ֵ����Ҫ��ȡ�����������ֽ���
			 */
			int contentLen = Integer.parseInt(headerStr.substring(
					headerStr.indexOf("Content-Length: ") + 16,
					headerStr.length()));

			/**
			 * ����ȡ��������ʱ���浽�����У��Ա�֮��д���ļ�
			 */
			byte[] buffer = new byte[8192];

			/**
			 * һ�ζ�ȡ���ֽ���
			 */
			int bytesRead = 0;

			/**
			 * ������ǰ����ȡ���ֽ���
			 */
			int totalRead = 0;

			/**
			 * ��Socket����������ѭ����ȡ����
			 */
			while ((bytesRead = in.read(buffer)) != -1) {
				/**
				 * ����ȡ������д���ļ�
				 */
				fout.write(buffer, 0, bytesRead);
				fout.flush();

				/**
				 * ����ȡ����contentLen���ֽ�ʱ��ֹͣ��ȡ
				 */
				totalRead += bytesRead;
				if (totalRead >= contentLen)
					break;
			}
		} catch (IOException e) {
			logger.log(Level.WARNING, connection.getRemoteSocketAddress()
					+ "����PUT����ʱ�����ļ�ʧ��", e);
			/**
			 * PUT����ʧ��ʱ,�����ͻ��˵�HTMLҳ��
			 */
			String failBody = getHTMLResponse("Internal Server Error",
					"HTTP Error 500: Internal Server Error");

			/**
			 * ����MIMEͷ����Ϣ
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
		 * PUT����ɹ�ʱ,�����ͻ��˵�HTMLҳ��
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
		 * ����MIMEͷ����Ϣ
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
	 * ����GET����ľ������
	 * 
	 * @param raw
	 *            ���͵��ͻ��˵��ֽ���
	 * @param out
	 *            ���͵��ͻ��˵��ַ���
	 * @param fileName
	 *            �������Դ��
	 * @param version
	 *            Э��汾
	 * @throws IOException
	 *             �͵��ͻ��˵����׳����쳣
	 */
	private void processGetRequest(OutputStream raw, Writer out,
			String fileName, String version) throws IOException {

		/**
		 * ����ָ�����ʵ���Դ����Ĭ��Ϊ��ҳ
		 */
		if (fileName.equals("/"))
			fileName += indexFileName;		
		

		/**
		 * �����Դ��ʵ��
		 */
		File theFile = null;
		if (fileName.startsWith("/")){
			theFile = new File(rootDirectory, fileName.substring(1,
					fileName.length()));
		}else{
			theFile = new File(rootDirectory, fileName);
		}
		


		/**
		 * �����Դ�����ҿɶ�
		 */
		if (theFile != null && theFile.exists() && theFile.canRead()) {
			/**
			 * �����Դ��ȫ�����ݣ����浽�ֽ������У��Ա㷢�͵��ͻ���
			 */
			byte[] theData = Files.readAllBytes(theFile.toPath());
			
			/**
			 * �����Դ��Ӧ��MIME����
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
			 * �Ҳ�����Դʱ���͵�HTMLҳ��
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
	 * ���췢�͵��ͻ��˵ļ�HTMLҳ��
	 * 
	 * @param title
	 *            ��ҳ����
	 * @param body
	 *            ��ҳ������
	 * @return ��HTMLҳ�������ַ���
	 */
	private String getHTMLResponse(String title, String body) {
		String htmlStr = new StringBuilder("<HTML>\r\n").append("<HEAD>")
				.append(title).append("</TITLE>\r\n").append("</HEAD>\r\n")
				.append("<BODY>").append("<H1>").append(body)
				.append("</H1>\r\n").append("</BODY></HTML>\r\n").toString();
		return htmlStr;
	}

	/**
	 * ���첢������Ӧ����ͷ����Ϣ
	 * 
	 * @param out
	 *            ���͵��ͻ��˵�����ַ���
	 * @param responseCode
	 *            ��Ӧ��
	 * @param contentType
	 *            MIME����
	 * @param length
	 *            ���͵����ݳ���
	 * @throws IOException
	 *             ���͵��ͻ��˵�����ַ����׳����쳣
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