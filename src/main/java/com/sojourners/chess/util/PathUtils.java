package com.sojourners.chess.util;

import com.sun.jna.Platform;

import java.io.File;

/**
 * Path 工具类
 */
public class PathUtils {
    public static String getJarPath() {
        try {
            String path = PathUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            path = java.net.URLDecoder.decode(path, "UTF-8");
            if (Platform.isWindows() && path.startsWith("/")) {
                path = path.substring(1);
            }
            int i = path.lastIndexOf("/");
            if (i >= 0) {
                path = path.substring(0, i + 1);
            }
            return path;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取 path 的父目录
     *
     * @param path
     * @return
     */
    public static File getParentDir(String path) {
        return new File(path).getParentFile();
    }

    public static boolean isImage(String path) {
        String[] paths = path.split("\\.");
        String suffix = paths[paths.length - 1].toLowerCase();
        if (suffix.equals("png") || suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("bmp")) {
            return true;
        }
        return false;
    }
}
