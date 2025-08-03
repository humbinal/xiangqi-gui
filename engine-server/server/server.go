package server

import (
	"bufio"
	"fmt"
	"github.com/gorilla/websocket"
	"github.com/klauspost/cpuid/v2"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

var engineBasePath string
var engineExecPath string

func StartServer(pikafishPath string, isa string) {
	// 1. 参数校验
	// 1.1. 判断 pikafish 目录是否存在
	if _, err := os.Stat(pikafishPath); os.IsNotExist(err) {
		log.Fatalf("Pikafish directory not found: %s", pikafishPath)
	}

	// 1.2. 根据当前运行的操作系统判断指定的指令集文件是否存在
	goos := runtime.GOOS
	var exeFileName string

	switch goos {
	case "linux":
		exeFileName = fmt.Sprintf("pikafish-%s", isa)
	case "windows":
		exeFileName = fmt.Sprintf("pikafish-%s.exe", isa)
	case "darwin":
		// 针对MacOS忽略传入的指令集参数
		exeFileName = "pikafish-apple-silicon"
		fmt.Println("Ignoring ISA parameter on macOS, using pikafish-apple-silicon")
	default:
		log.Fatalf("Unsupported operating system: %s", goos)
	}

	var exePath string
	switch goos {
	case "linux":
		exePath = filepath.Join(pikafishPath, "Linux", exeFileName)
	case "windows":
		exePath = filepath.Join(pikafishPath, "Windows", exeFileName)
	case "darwin":
		exePath = filepath.Join(pikafishPath, "MacOS", exeFileName)
	}

	if _, err := os.Stat(exePath); os.IsNotExist(err) {
		log.Fatalf("Pikafish executable not found: %s", exePath)
	}

	// 1.3. 判断当前运行设备的CPU是否支持该指令集 (MacOS除外)
	if goos != "darwin" {
		if !checkCpuFeature(isa) {
			log.Fatalf("The current CPU does not support the specified instruction set: %s", isa)
		}
	}

	fmt.Println("start server, pikafishPath:", pikafishPath, "executable:", exePath)
	// 接续执行服务器启动的逻辑...
	engineBasePath = pikafishPath
	engineExecPath = exePath
	serve()
}

// 检查CPU是否支持特定的指令集.
func checkCpuFeature(feature string) bool {
	feature = strings.ToUpper(feature)
	switch feature {
	case "VNNI512":
		return cpuid.CPU.Has(cpuid.AVX512VNNI)
	case "AVX512":
		return cpuid.CPU.Has(cpuid.AVX512F)
	case "AVX512F":
		return cpuid.CPU.Has(cpuid.AVX512F)
	case "AVXVNNI":
		return cpuid.CPU.Has(cpuid.AVXVNNI)
	case "BMI2":
		return cpuid.CPU.Has(cpuid.BMI2)
	case "AVX2":
		return cpuid.CPU.Has(cpuid.AVX2)
	case "SSE41-POPCNT":
		return cpuid.CPU.Has(cpuid.SSE4) && cpuid.CPU.Has(cpuid.POPCNT)
	default:
		return false
	}
}

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

	// 启动Pikafish引擎子进程
	cmd := exec.Command(engineExecPath)
	// 设置子进程的工作目录为可执行文件所在的目录
	cmd.Dir = engineBasePath
	log.Printf("为引擎设置工作目录: %s", cmd.Dir)

	engineStdin, _ := cmd.StdinPipe()
	engineStdout, _ := cmd.StdoutPipe()

	if err := cmd.Start(); err != nil {
		log.Printf("错误: 启动Pikafish引擎 '%s' 失败: %v", engineExecPath, err)
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

func serve() {
	http.HandleFunc("/ws", handleConnection)
	port := "8080"
	log.Printf("Go WebSocket服务器已启动，监听地址: ws://localhost:%s/ws", port)
	if err := http.ListenAndServe(":"+port, nil); err != nil {
		log.Fatalf("服务器启动失败: %v", err)
	}
}
