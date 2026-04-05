# 阿里云ECS部署指南

本文档描述如何在阿里云ECS上部署zeChat项目（Spring Boot + Nginx）。

## 环境要求

- JDK 21
- Nginx
- 阿里云ECS（公网IP已配置）
- 安全组已开放80/443端口

---

## 部署步骤

### 第一步：打包项目

在本地项目目录执行：

```bash
./mvnw clean package -DskipTests
```

生成文件：`target/zeChat-0.0.1-SNAPSHOT.jar`

---

### 第二步：将包上传到ECS

```bash
scp target/zeChat-0.0.1-SNAPSHOT.jar root@你的ECS公网IP:/opt/zechat/
```

---

### 第三步：在ECS上安装Java 21

```bash
# 方法1: 使用apt (Ubuntu/Debian)
apt update && apt install openjdk-21-jdk

# 方法2: 下载JDK (推荐)
cd /opt
wget https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz
tar -xzf jdk-21_linux-x64_bin.tar.gz
export JAVA_HOME=/opt/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```

---

### 第四步：安装Nginx

```bash
# Ubuntu/Debian
apt install nginx

# CentOS
yum install nginx
```

---

### 第五步：配置Nginx反向代理

创建Nginx配置文件：

```bash
vim /etc/nginx/sites-available/zechat
```

写入以下内容：

```nginx
server {
    listen 80;
    server_name 你的域名或ECS公网IP;

    # 前端静态文件 (如果有)
    location / {
        root /var/www/zechat;
        index index.html;
        try_files $uri $uri/ @proxy;
    }

    # Spring Boot后端API (WebSocket支持)
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 健康检查
    location /actuator/health {
        proxy_pass http://127.0.0.1:8080/actuator/health;
        proxy_set_header Host $host;
    }
}
```

启用配置：

```bash
ln -s /etc/nginx/sites-available/zechat /etc/nginx/sites-enabled/
nginx -t  # 测试配置
systemctl reload nginx
```

---

### 第六步：上传前端静态文件（如果有）

```bash
# 如果有静态HTML文件
scp -r src/main/resources/static/* root@你的ECS公网IP:/var/www/zechat/
```

---

### 第七步：创建Spring Boot启动脚本

```bash
vim /opt/zechat/start.sh
```

```bash
#!/bin/bash
export JAVA_HOME=/opt/jdk-21
export PATH=$JAVA_HOME/bin:$PATH

cd /opt/zechat
nohup $JAVA_HOME/bin/java -jar zeChat-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 \
  --server.port=8080 \
  > app.log 2>&1 &
```

```bash
chmod +x /opt/zechat/start.sh
```

---

### 第八步：开放安全组端口

在阿里云控制台 → ECS → 安全组 → 配置规则：

| 协议 | 端口范围 | 授权对象 |
|------|----------|----------|
| TCP | 80 | 0.0.0.0/0 |
| TCP | 443 | 0.0.0.0/0 |
| TCP | 8080 | 127.0.0.1 (仅限本地) |

---

### 第九步：启动服务

```bash
# 启动Spring Boot
/opt/zechat/start.sh

# 检查状态
curl http://127.0.0.1:8080/actuator/health
ps aux | grep zeChat
tail -f /opt/zechat/app.log
```

---

### 第十步：配置HTTPS（可选但推荐）

使用Let's Encrypt免费证书：

```bash
apt install certbot python3-certbot-nginx
certbot --nginx -d your-domain.com
```

---

### 第十一步：配置开机自启（systemd）

```bash
vim /etc/systemd/system/zechat.service
```

```ini
[Unit]
Description=zeChat Spring Boot Application
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/zechat
ExecStart=/opt/jdk-21/bin/java -jar zeChat-0.0.1-SNAPSHOT.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable zechat
systemctl start zechat
```

---

## 访问方式

- HTTP访问：`http://你的ECS公网IP`
- HTTPS访问：`https://你的域名`

---

## 常见问题排查

```bash
# 检查端口占用
netstat -tlnp | grep 8080

# 查看Nginx日志
tail -f /var/log/nginx/error.log

# 查看Spring Boot日志
tail -f /opt/zechat/app.log

# 重启服务
systemctl restart zechat
systemctl restart nginx
```

---

## 目录结构

部署后的目录结构：

```
/opt/zechat/
├── zeChat-0.0.1-SNAPSHOT.jar
├── start.sh
└── app.log

/var/www/zechat/
├── index.html
└── static/ (如果有)

/etc/nginx/sites-available/zechat
```
