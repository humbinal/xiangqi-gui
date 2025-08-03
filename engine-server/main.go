package main

import (
	"bufio"
	"fmt"
	"io"
	"log"
	"net/http"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/gorilla/websocket"
)

// upgrader 将HTTP连接升级为WebSocket连接
var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true // 允许所有跨域请求，方便开发
	},
}

// handleConnection 为每一个客户端连接创建一个独立的Pikafish引擎实例
func handleConnection(w http.ResponseWriter, r *http.Request) {
	// 升级HTTP为WebSocket
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("错误: 升级WebSocket失败: %v", err)
		return
	}
	defer conn.Close()
	log.Println("新客户端已连接.")

	// 确定Pikafish可执行文件路径 (Windows/Linux)
	enginePath := "./pikafish"
	if runtime.GOOS == "windows" {
		enginePath = "D:\\Downloads\\Pikafish.2025-06-23\\Windows\\pikafish-avxvnni.exe"
	}

	// 启动Pikafish引擎子进程
	cmd := exec.Command(enginePath)
	// 设置子进程的工作目录为可执行文件所在的目录
	cmd.Dir = filepath.Dir(enginePath)
	log.Printf("为引擎设置工作目录: %s", cmd.Dir)

	engineStdin, _ := cmd.StdinPipe()
	engineStdout, _ := cmd.StdoutPipe()

	if err := cmd.Start(); err != nil {
		log.Printf("错误: 启动Pikafish引擎 '%s' 失败: %v", enginePath, err)
		return
	}
	log.Println("Pikafish引擎进程已为该客户端启动.")

	// 使用defer确保在客户端断开连接时，引擎进程一定会被终止
	defer func() {
		log.Println("正在终止引擎进程...")
		if err := cmd.Process.Kill(); err != nil {
			// 在进程已经自己退出的情况下，Kill会报错，这是正常的
			log.Printf("警告: 无法杀死引擎进程 (可能已自行退出): %v", err)
		} else {
			log.Println("Pikafish引擎进程已成功终止.")
		}
	}()

	// 启动一个goroutine，负责将引擎的输出(stdout)转发给WebSocket客户端
	go forwardEngineOutput(engineStdout, conn)

	// 在主goroutine中，循环读取WebSocket客户端的消息，并转发给引擎的输入(stdin)
	for {
		_, message, err := conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("错误: 读取客户端消息异常: %v", err)
			} else {
				log.Println("客户端已主动断开连接.")
			}
			break // 客户端断开连接，退出循环，触发defer中的进程清理
		}

		command := strings.TrimSpace(string(message))
		log.Printf("客户端 -> 引擎: %s", command)

		// 将收到的指令写入引擎的标准输入
		if _, err := fmt.Fprintln(engineStdin, command); err != nil {
			log.Printf("错误: 发送指令到引擎失败: %v", err)
			break
		}
	}
}

// forwardEngineOutput 负责从引擎进程的标准输出读取数据并发送到WebSocket
func forwardEngineOutput(engineStdout io.ReadCloser, conn *websocket.Conn) {
	scanner := bufio.NewScanner(engineStdout)
	for scanner.Scan() {
		line := scanner.Text()
		if line != "" {
			log.Printf("引擎 -> 客户端: %s", line)
			// conn.WriteMessage 不是线程安全的，如果需要并发写，要加锁
			// 但在这里，只有一个goroutine在写，所以是安全的
			if err := conn.WriteMessage(websocket.TextMessage, []byte(line)); err != nil {
				log.Printf("警告: 发送消息到客户端失败 (可能已断开): %v", err)
				return // 客户端已断开，退出此goroutine
			}
		}
	}
	if err := scanner.Err(); err != nil {
		log.Printf("警告: 读取引擎输出时出错: %v", err)
	}
}

func main() {
	http.HandleFunc("/ws", handleConnection)

	port := "8080"
	log.Printf("Go WebSocket服务器已启动，监听地址: ws://localhost:%s/ws", port)
	log.Println("请确保Pikafish可执行文件与本程序在同一目录下。")

	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("服务器启动失败: %v", err)
	}
}
