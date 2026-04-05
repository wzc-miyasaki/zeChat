#!/bin/bash

# 自動獲取 java 執行檔所在的目錄，並推算出 JAVA_HOME
# dirname $(which java) 會得到 .../bin，再套一層 dirname 得到主目錄
#export JAVA_HOME=$(dirname $(dirname $(which java)))
#export PATH=$JAVA_HOME/bin:$PATH

cd /root

# 啟動命令
nohup java -jar zeChat-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 \
  --server.port=8080 \
  > app.log 2>&1 &
