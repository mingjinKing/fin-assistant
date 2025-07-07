#!/bin/bash
# 在项目目录中
# python3 -m venv .venv
# source .venv/bin/activate
nohup python3 ./combined_service.py > ./service.log 2>&1 &
echo "Embedding service started. PID: $!"