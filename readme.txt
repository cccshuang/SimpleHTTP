基于Java Socket构建简单的HTTP的客户端和服务器
1)	服务器的根目录，即http://<computer>.<domain>/ 应指向在命令行中传递给程序的目录。例如，根目录设置为服务器端c:\websrc目录，则当客户端请求GET /test/index.html时，服务器应向客户端发送文件C:\\websrc\test\index.html来响应请求，如果此文件不存在，则返回404响应。
2)	服务器可从文件的扩展名中推导出一个文件的MIME类型，例如，文件名以.htm或.html结尾的文件假定为text / html类型。
3)	服务器可识别HTML和JPEG文件等，即文件扩展名分别为.htm和.jpg的文件。服务器能够将.jpg图像嵌入到HTML文档中。
4)	支持并发接收请求.
