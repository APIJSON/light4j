# light4j

#### 项目简介
使用light-4j构建的微服务接口应用

#### 本地测试
1. 项目构建：sh start.sh package
2. 启动缓存：redis-server
3. 运行项目：start.bat
4. 测试访问：[logs](http://localhost:8080/ws/logs.html)，[datetime](http://localhost:8080/service/datetime.json)

#### 线上部署
1. 项目打包：sh start.sh deploy
2. 提取脚本：jar xvf light4j.jar start.sh
3. 运行服务：sh start.sh start

#### 配置说明
vi start.sh

1. -Dredis.configDb，配置redis地址
2. -Dlight4j.directory，可以从light4j.yml里面的网址下载文件
3. -Dlogserver，在/etc/hosts配置logserver地址
4. contextName=light4j，修改应用的日志上下文
5. -Djava.compiler=none，禁用JIT可节约内存，默认启用JIT可提高性能
6. https、registry可自行研究，sh start.sh keystore转换密钥为相关文件

#### 其他说明

1. WeixinHandler支持响应微信公众号消息，关注xlongwei试试[help](https://api.xlongwei.com/service/weixin/chat.json?text=help)
2. LayuiHandler和openapi支持前后端分离，参考[admin](http://layui.xlongwei.com/admin/)
3. WebSocketHandlerProvider支持web socket消息，参考[chat](https://api.xlongwei.com:8443/ws/chat.html)
