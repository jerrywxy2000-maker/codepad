#!/usr/bin/env zsh
# CodePad USB 桥接：用 adb reverse 把平板的 127.0.0.1:39876 转发到 Mac 的 39876。
# 平板端 App 每次重连都优先探测这条 loopback 通道，成功则状态栏显示
# 「USB 数据线」；未建立转发时连接会被内核瞬间拒绝，自动退回 Bonjour Wi-Fi。
# 也就是说本脚本不是必需项，只是让有线连接更稳、延迟更低。
#
# 用法：
#   scripts/usb-bridge.sh           建立转发（默认）
#   scripts/usb-bridge.sh status    查看当前转发列表
#   scripts/usb-bridge.sh down      移除本脚本建立的转发
#
# 前提：平板打开「开发者选项 → USB 调试」，插线后在平板上允许本机调试。
# 端口必须与 Mac helper 的 Wire.port（默认 39876）一致。
set -euo pipefail

PORT=39876
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[[ -x "$ADB" ]] || ADB="$(command -v adb || true)"
if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "找不到 adb：请安装 Android SDK platform-tools，或把 adb 加入 PATH。" >&2
  exit 1
fi

cmd="${1:-up}"

attached_serials() {
  # 只取状态为 device 的（排除 unauthorized/offline），多设备逐个处理。
  "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

case "$cmd" in
  up)
    "$ADB" start-server >/dev/null
    raw="$(attached_serials)"
    serials=()
    [[ -n "$raw" ]] && serials=("${(@f)raw}")
    if (( ${#serials} == 0 )); then
      echo "未检测到已授权的 Android 设备：" >&2
      echo "  1) 插好 USB 线" >&2
      echo "  2) 平板开启「开发者选项 → USB 调试」" >&2
      echo "  3) 平板弹窗时允许本机调试（勾选始终允许）" >&2
      exit 1
    fi
    for serial in "$serials[@]"; do
      "$ADB" -s "$serial" reverse "tcp:$PORT" "tcp:$PORT"
      echo "已建立 USB 转发：$serial  平板:$PORT → Mac:$PORT"
    done
    echo
    echo "当前转发列表："
    "$ADB" reverse --list | sed 's/^/  /'
    echo
    echo "打开平板上的 CodePad，状态栏显示「USB 数据线」即为生效。"
    ;;
  down)
    raw="$(attached_serials)"
    serials=()
    [[ -n "$raw" ]] && serials=("${(@f)raw}")
    for serial in "$serials[@]"; do
      "$ADB" -s "$serial" reverse --remove "tcp:$PORT" 2>/dev/null || true
    done
    echo "已移除 tcp:$PORT 的转发。"
    ;;
  status)
    if list="$("$ADB" reverse --list 2>/dev/null)"; then
      if [[ -n "$list" ]]; then
        print -r -- "$list" | sed 's/^/  /'
      else
        echo "  （当前没有转发）"
      fi
    else
      echo "  （未连接设备或 adb server 未运行）"
    fi
    ;;
  -h|--help|help)
    awk 'NR > 1 { if (/^#/) sub(/^# ?/, ""); else exit; print }' "$0"
    ;;
  *)
    echo "未知子命令: $cmd（可用：up / status / down）" >&2
    exit 2
    ;;
esac
