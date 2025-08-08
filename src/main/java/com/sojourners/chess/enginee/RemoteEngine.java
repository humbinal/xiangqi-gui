package com.sojourners.chess.enginee;

import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 引擎封装 (已修改为WebSocket网络版本 - 精确修正test方法)
 */
public class RemoteEngine extends Engine {

    private WebSocketClient webSocketClient;
    private final String protocol;
    private Engine.AnalysisModel analysisModel;
    private long analysisValue;
    private volatile boolean threadNumChange;
    private int threadNum;
    private volatile boolean hashSizeChange;
    private int hashSize;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final EngineCallBack cb;
    private final Thread readerThread;
    private final Random random;


    public RemoteEngine(EngineConfig ec, EngineCallBack cb) throws IOException {
        this.protocol = ec.getProtocol();
        this.cb = cb;
        this.random = new SecureRandom();

        String url = ec.getPath();
        System.out.println("url: " + url);
        String serverUrl = "ws://" + url + "/ws";

        try {
            URI serverUri = new URI(serverUrl);
            System.out.println("INFO: 正在连接到引擎服务器: " + serverUri);

            this.webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("INFO: 已成功连接到WebSocket服务器。");
                    Thread.startVirtualThread(() -> {
                        cmd(protocol);
                        for (Map.Entry<String, String> entry : ec.getOptions().entrySet()) {
                            if ("uci".equals(protocol)) {
                                cmd("setoption name " + entry.getKey() + " value " + entry.getValue());
                            } else if ("ucci".equals(protocol)) {
                                cmd("setoption " + entry.getKey() + " " + entry.getValue());
                            }
                        }
                    });
                }

                @Override
                public void onMessage(String message) {
                    try {
                        messageQueue.put(message);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("ERROR: 消息队列在放入消息时被中断: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("INFO: WebSocket连接已关闭. Code: " + code + ", Reason: " + reason);
                    messageQueue.add("event:disconnect");
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("ERROR: WebSocket发生错误: " + ex.getMessage());
                    messageQueue.add("event:disconnect");
                }
            };

            this.readerThread = Thread.startVirtualThread(() -> {
                try {
                    String line;
                    while (!(line = messageQueue.take()).equals("event:disconnect")) {
                        System.out.println("SERVER -> CLIENT: " + line);
                        if (line.contains("nps")) {
                            thinkDetail(line);
                        } else if (line.contains("bestmove")) {
                            bestMove(line);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("INFO: 引擎消息读取线程已中断。");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("INFO: 引擎消息循环已退出。");
            });

            this.webSocketClient.connect();

        } catch (Exception e) {
            throw new IOException("创建WebSocket连接失败", e);
        }
    }

    // --- 以下是该类的其他方法，保持不变 ---
    // ... sleep, validateMove, bestMove, thinkDetail, analysis (both versions),
    // ... stop, setThreadNum, setHashSize, setAnalysisModel ...
    // ... 我们将只替换 test 和 cmd 以及 close 方法 ...

    /**
     * 【核心修正】
     * 重新实现的 test 方法。此方法通过一次性的WebSocket连接来探测远程引擎的协议和选项。
     *
     * @param filePath 引擎路径（在新架构下被忽略，仅用于兼容性）
     * @param options  一个空的Map，此方法将用从引擎获取的选项来填充它
     * @return "uci" 或 "ucci"，如果探测成功；否则返回 null
     */
    public static String test(String filePath, LinkedHashMap<String, String> options) {
        System.out.println("INFO: 正在执行远程引擎test, url=" + filePath);
        String serverUrl = "ws://" + filePath + "/ws";
        WebSocketClient testClient = null;
        // 使用 CompletableFuture 来等待异步的网络响应
        CompletableFuture<String> protocolFuture = new CompletableFuture<>();

        try {
            testClient = new WebSocketClient(new URI(serverUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("INFO: [Test] 探测连接已建立。正在发送 'uci' 指令...");
                    send("uci");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("INFO: [Test] 收到消息: " + message);
                    // 检查是否是协议确认消息
                    if ("uciok".equals(message)) {
                        // 如果尚未完成，则完成Future
                        if (!protocolFuture.isDone()) {
                            System.out.println("INFO: [Test] 收到 uciok，协议确认为 uci。");
                            protocolFuture.complete("uci");
                        }
                    } else if ("ucciok".equals(message)) {
                        if (!protocolFuture.isDone()) {
                            System.out.println("INFO: [Test] 收到 ucciok，协议确认为 ucci。");
                            protocolFuture.complete("ucci");
                        }
                    }
                    // 检查是否是引擎选项信息
                    else if (message.startsWith("option name") && message.contains("type") && message.contains("default")
                            && !message.contains("Threads") && !message.contains("Hash")) {
                        try {
                            String[] parts = message.split("name | type | default ");
                            if (parts.length >= 4) {
                                String key = parts[1].trim();
                                String value = parts[3].trim().split(" ")[0];
                                System.out.println("INFO: [Test] 解析到选项: " + key + " = " + value);
                                options.put(key, value);
                            }
                        } catch (Exception e) {
                            System.err.println("WARN: [Test] 解析选项行失败: '" + message + "' - " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("INFO: [Test] 探测连接已关闭. Reason: " + reason);
                    // 如果连接关闭了但我们还没得到结果，说明探测失败
                    if (!protocolFuture.isDone()) {
                        protocolFuture.complete(null);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("ERROR: [Test] 探测连接出错: " + ex.getMessage());
                    protocolFuture.completeExceptionally(ex);
                }
            };

            System.out.println("INFO: [Test] 正在连接到 " + serverUrl);
            testClient.connect();

            // 阻塞并等待结果，设置一个合理的超时时间（例如5秒）
            String detectedProtocol = protocolFuture.get(5, TimeUnit.SECONDS);

            // 如果第一次用uci没探测到，可以尝试ucci（虽然对于Pikafish这不太可能发生）
            if (detectedProtocol == null) {
                System.out.println("INFO: [Test] 'uci' 探测无响应，尝试 'ucci'...");
                testClient.send("ucci");
                // 再次等待
                detectedProtocol = protocolFuture.get(5, TimeUnit.SECONDS);
            }

            System.out.println("INFO: [Test] 探测完成，协议为: " + detectedProtocol);
            return detectedProtocol;

        } catch (Exception e) {
            System.err.println("ERROR: 引擎探测期间发生严重错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        } finally {
            // 确保无论如何都关闭这个一次性的客户端
            if (testClient != null) {
                testClient.close();
                System.out.println("INFO: [Test] 一次性探测客户端已关闭。");
            }
        }
    }

    private void cmd(String command) {
        System.out.println("CLIENT -> SERVER: " + command);
        try {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(command);
            } else {
                System.err.println("ERROR: 无法发送指令，WebSocket未连接: " + command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (webSocketClient != null && webSocketClient.isOpen()) {
                cmd("quit");
                webSocketClient.close();
            }

            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 其他所有方法 (sleep, bestMove, thinkDetail 等) 都保持您提供的版本不变 ---
    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            e.printStackTrace();
        }
    }

    private boolean validateMove(String move) {
        if (StringUtils.isEmpty(move) || move.length() != 4) {
            return false;
        }
        char c0 = move.charAt(0), c2 = move.charAt(2);
        char c1 = move.charAt(1), c3 = move.charAt(3);
        return c0 >= 'a' && c0 <= 'i' && c2 >= 'a' && c2 <= 'i' &&
                c1 >= '0' && c1 <= '9' && c3 >= '0' && c3 <= '9';
    }

    private void bestMove(String msg) {
        String[] str = msg.split(" ");
        if (str.length < 2 || !validateMove(str[1])) {
            return;
        }
        if (Properties.getInstance().getEngineDelayEnd() > 0 && Properties.getInstance().getEngineDelayEnd() >= Properties.getInstance().getEngineDelayStart()) {
            int t = random.nextInt(Properties.getInstance().getEngineDelayStart(), Properties.getInstance().getEngineDelayEnd());
            sleep(t);
        }
        cb.bestMove(str[1], str.length == 4 ? str[3] : null);
    }

    private void thinkDetail(String msg) {
        // 此方法内部逻辑无需修改
        String[] str = msg.split(" ");
        ThinkData td = new ThinkData();
        List<String> detail = new ArrayList<>();
        td.setDetail(detail);
        int flag = 0;
        for (int i = 0; i < str.length; i++) {
            if (flag != 0) {
                if (flag == 6) {
                    detail.add(str[i]);
                } else {
                    if (StringUtils.isDigit(str[i])) {
                        if (flag == 1) {
                            td.setNps(Long.parseLong(str[i]));

                        } else if (flag == 2) {
                            td.setTime(Long.parseLong(str[i]));

                        } else if (flag == 3) {
                            td.setDepth(Integer.parseInt(str[i]));
                        } else if (flag == 4) {
                            td.setMate(Integer.parseInt(str[i]));

                        } else if (flag == 5) {
                            td.setScore(Integer.parseInt(str[i]));
                        }
                        flag = 0;
                    }
                }
            } else {
                if ("depth".equals(str[i])) {
                    flag = 3;
                } else if ("score".equals(str[i])) {
                    if (i + 1 < str.length && "mate".equals(str[i + 1])) {
                        flag = 4;
                    } else {
                        flag = 5;
                    }
                } else if ("mate".equals(str[i])) {
                    flag = 4;
                } else if ("nps".equals(str[i])) {
                    flag = 1;
                } else if ("time".equals(str[i])) {
                    flag = 2;
                } else if ("pv".equals(str[i])) {
                    flag = 6;
                }
            }
        }
        if (!td.getDetail().isEmpty()) {
            cb.thinkDetail(td);
        }
    }

    @Override
    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo) {
        Thread.startVirtualThread(() -> {
            if (Properties.getInstance().getBookSwitch()) {
                long s = System.currentTimeMillis();
                List<BookData> results = OpenBookManager.getInstance().queryBook(board, redGo, moves.size() / 2 >= Properties.getInstance().getOffManualSteps());
                System.out.println("查询库时间" + (System.currentTimeMillis() - s));
                this.cb.showBookResults(results);
                if (!results.isEmpty() && this.analysisModel != Engine.AnalysisModel.INFINITE) {
                    if (Properties.getInstance().getBookDelayEnd() > 0 && Properties.getInstance().getBookDelayEnd() >= Properties.getInstance().getBookDelayStart()) {
                        int t = random.nextInt(Properties.getInstance().getBookDelayStart(), Properties.getInstance().getBookDelayEnd());
                        sleep(t);
                    }
                    this.cb.bestMove(results.get(0).getMove(), null);
                    return;
                }
            }
            this.analysis(fenCode, moves);
        });
    }

    private void analysis(String fenCode, List<String> moves) {
        stop();

        if (threadNumChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Threads value " : "setoption Threads ") + threadNum);
            this.threadNumChange = false;
        }
        if (hashSizeChange) {
            cmd(("uci".equals(this.protocol) ? "setoption name Hash value " : "setoption Hash ") + hashSize);
            this.hashSizeChange = false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("position fen ").append(fenCode);
        if (moves != null && !moves.isEmpty()) {
            sb.append(" moves");
            for (String move : moves) {
                sb.append(" ").append(move);
            }
        }
        cmd(sb.toString());

        if (analysisModel == Engine.AnalysisModel.FIXED_STEPS) {
            cmd("go depth " + analysisValue);
        } else if (analysisModel == Engine.AnalysisModel.FIXED_TIME) {
            cmd("go movetime " + analysisValue);
        } else {
            cmd("go infinite");
        }
    }

    @Override
    public void stop() {
        cmd("stop");
    }

    @Override
    public void setThreadNum(int threadNum) {
        if (threadNum != this.threadNum) {
            this.threadNum = threadNum;
            this.threadNumChange = true;
        }
    }

    @Override
    public void setHashSize(int hashSize) {
        if (hashSize != this.hashSize) {
            this.hashSize = hashSize;
            this.hashSizeChange = true;
        }
    }

    @Override
    public void setAnalysisModel(Engine.AnalysisModel model, long v) {
        this.analysisModel = model;
        this.analysisValue = v;
    }
}