#!/bin/bash

# 使用 lsof 获取占用 5000 端口的 PID
PID=$(lsof -t -i :5000)

if [ -z "$PID" ]; then
  echo "Service is not running on port 5000."
else
  kill $PID
  echo "Service stopped (PID: $PID)."
fi
