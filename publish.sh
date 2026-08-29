#!/bin/bash
# GitHub 一键发布脚本
# 用法: ./publish.sh <你的GitHub用户名> <仓库名>
set -e
USER=$1; REPO=${2:-iceReading}
[ -z "$USER" ] && { echo "用法: ./publish.sh <用户名> [仓库名]"; exit 1; }
cd "$(dirname "$0")"
git init -q 2>/dev/null || true
git add -A
git commit -qm "iceReading v1.0.0 — 纯 Java 无依赖 EPUB 阅读器" 2>/dev/null || true
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$USER/$REPO.git"
echo "推送到 https://github.com/$USER/$REPO"
git push -u origin main
echo "完成! 仓库地址: https://github.com/$USER/$REPO"
echo "提示: 首次push需GitHub token认证(gh auth login 或 Personal Access Token)"
