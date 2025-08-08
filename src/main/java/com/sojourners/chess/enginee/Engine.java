package com.sojourners.chess.enginee;

import java.util.LinkedHashMap;
import java.util.List;

public abstract class Engine {

    public enum AnalysisModel {
        FIXED_TIME,
        FIXED_STEPS,
        INFINITE
    }

    public enum Type {
        LOCAL,
        REMOTE,
    }

    public static String test(Type type, String path, LinkedHashMap<String, String> options) {
        return switch (type) {
            case LOCAL -> LocalEngine.test(path, options);
            case REMOTE -> RemoteEngine.test(path, options);
        };
    }

    /**
     * 设置线程数
     */
    public abstract void setThreadNum(int threadNum);

    /**
     * 设置哈希大小
     */
    public abstract void setHashSize(int hashSize);

    /**
     * 设置分析模式
     */
    public abstract void setAnalysisModel(AnalysisModel model, long v);

    /**
     * 执行分析
     */
    public abstract void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo);

    /**
     * 停止分析
     */
    public abstract void stop();

    /**
     * 关闭引擎连接
     */
    public abstract void close();


}
