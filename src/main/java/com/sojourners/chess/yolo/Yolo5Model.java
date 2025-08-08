package com.sojourners.chess.yolo;


import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class Yolo5Model extends OnnxModel {

    @Override
    public String getModelPath() {
        return "model/middle.onnx";
    }

    /**
     * 寻找棋盘范围，用于后续连线识别
     *
     * @param img
     * @return
     */
    @Override
    public java.awt.Rectangle findBoardPosition(BufferedImage img) {
        try {
            if (img == null) {
                return null;
            }
            // 图像宽高的缩放比例
            List<DetectResult> results = this.predict(img);
            // 寻找棋盘
            java.awt.Rectangle pos = findBoardPosition(results);
            if (pos == null) {
                return null;
            }
            // 棋盘范围
            double pieceWidth = pos.width / 8d, pieceHeight = pos.height / 9d;
            pos.x -= pieceWidth * PADDING;
            if (pos.x < 0) {
                pos.x = 0;
            }
            pos.y -= pieceHeight * PADDING;
            if (pos.y < 0) {
                pos.y = 0;
            }
            pos.width += pieceWidth * PADDING * 2;
            if (pos.x + pos.width > img.getWidth()) {
                pos.width = img.getWidth() - pos.x;
            }
            pos.height += pieceHeight * PADDING * 2;
            if (pos.y + pos.height > img.getHeight()) {
                pos.height = img.getHeight() - pos.y;
            }
            return pos;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setBlankBoard(char[][] board) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = ' ';
            }
        }
    }

    private java.awt.Rectangle findBoardPosition(List<DetectResult> results) {
        int boardCount = 0;
        java.awt.Rectangle boardPos = new java.awt.Rectangle();
        // 先找到棋盘
        for (DetectResult obj : results) {
            char label = obj.label;
            Rectangle bound = obj.rect;
            if (label == '0') {
                // 取最大的棋盘区域
                int w = (int) (bound.getWidth()), h = (int) (bound.getHeight());
                if (w > boardPos.width && h > boardPos.height) {
                    boardPos.x = (int) (bound.getX() - w / 2d);
                    boardPos.y = (int) (bound.getY() - h / 2d);
                    boardPos.width = w;
                    boardPos.height = h;
                }
                boardCount++;
            }
        }
        if (boardCount == 0) {
            return null;
        }
        return boardPos;
    }

    /**
     * 根据图片识别棋子及其位置
     *
     * @param img
     * @return
     */
    @Override
    public boolean findChessBoard(BufferedImage img, char[][] board) {
        try {
            if (img == null) {
                return false;
            }
            // 图像宽高的缩放比例
            List<DetectResult> results = this.predict(img);
            setBlankBoard(board);
            java.awt.Rectangle boardPos = findBoardPosition(results);
            if (boardPos == null) {
                return false;
            }
            int pieceWidth = boardPos.width / 8, pieceHeight = boardPos.height / 9;
            // 再获取每个棋子及其位置
            for (DetectResult obj : results) {
                char label = obj.label;
                Rectangle bound = obj.rect;
                if (label != '0') {
                    int j = (int) ((bound.x - (boardPos.x - pieceWidth / 2)) / pieceWidth);
                    int i = (int) ((bound.y - (boardPos.y - pieceHeight / 2)) / pieceHeight);
                    if (i < 0 || i > 9 || j < 0 || j > 8) {
                        continue;
                    }
                    board[i][j] = label;
                }
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<DetectResult> predict(BufferedImage image) throws OrtException {

        List<DetectResult> list = null;

        float rate = ((float) SIZE) / Math.max(image.getWidth(), image.getHeight());
        long s = System.currentTimeMillis();
        float[][][] inputData = processInput(image, rate);
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, new float[][][][]{inputData})) {

            Map<String, OnnxTensor> container = new HashMap<>();
            container.put("images", inputTensor);
            try (OrtSession.Result results = session.run(container)) {

                for (Map.Entry<String, OnnxValue> r : results) {

                    OnnxValue resultValue = r.getValue();
                    OnnxTensor resultTensor = (OnnxTensor) resultValue;
                    float[] output = resultTensor.getFloatBuffer().array();

                    list = processOutput(output, image, rate);
                }
            }
        }

//        System.gc();
        System.out.println(System.currentTimeMillis() - s);
        return list;
    }

    float[][][] processInput(BufferedImage image, float rate) {

        int destW = Math.round(image.getWidth() * rate);
        int destH = Math.round(image.getHeight() * rate);
        BufferedImage resizedImage = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        // 改进的绘制参数设置
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); // 或者 VALUE_INTERPOLATION_BICUBIC
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(image, 0, 0, destW, destH, null);
        g2d.dispose();

        int resizedWidth = resizedImage.getWidth();
        int resizedHeight = resizedImage.getHeight();
        int leftMargin = 0, topMargin = 0;

        float[][][] arr = new float[3][SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (i >= topMargin && j >= leftMargin && i < topMargin + resizedHeight
                        && j < leftMargin + resizedWidth) {
                    int rgb = resizedImage.getRGB(j - leftMargin, i - topMargin);
                    Color color = new Color(rgb, true);
                    arr[0][i][j] = color.getRed() / 255.0f;
                    arr[1][i][j] = color.getGreen() / 255.0f;
                    arr[2][i][j] = color.getBlue() / 255.0f;
                } else {
                    arr[0][i][j] = 114.0f / 255;
                    arr[1][i][j] = 114.0f / 255;
                    arr[2][i][j] = 114.0f / 255;
                }
            }
        }
        return arr;
    }

    List<DetectResult> nms(List<DetectResult> list) {

        List<DetectResult> results = new ArrayList<>();

        for (int k = 0; k < 15; ++k) {
            PriorityQueue<DetectResult> pq = new PriorityQueue<>(50, (lhs, rhs) -> {
                return Double.compare(rhs.confidence, lhs.confidence);
            });
            Iterator var7 = list.iterator();

            while (var7.hasNext()) {
                DetectResult intermediateResult = (DetectResult) var7.next();
                if (intermediateResult.label == labels[k]) {
                    pq.add(intermediateResult);
                }
            }

            while (pq.size() > 0) {
                DetectResult[] a = new DetectResult[pq.size()];
                DetectResult[] detections = pq.toArray(a);

                results.add(detections[0]);

                pq.clear();

                for (int j = 1; j < detections.length; ++j) {
                    DetectResult detection = detections[j];
                    Rectangle location = detection.rect;
                    if (this.boxIou(detections[0].rect, location) < 0.45d) {
                        pq.add(detection);
                    }
                }
            }
        }

        return results;
    }

    private double boxIou(Rectangle a, Rectangle b) {
        return this.boxIntersection(a, b) / this.boxUnion(a, b);
    }

    private double boxUnion(Rectangle a, Rectangle b) {
        double i = this.boxIntersection(a, b);
        return a.getWidth() * a.getHeight() + b.getWidth() * b.getHeight() - i;
    }

    private double boxIntersection(Rectangle a, Rectangle b) {
        double w = this.overlap(a.getX(), a.getWidth(), b.getX(), b.getWidth());
        double h = this.overlap(a.getY(), a.getHeight(), b.getY(), b.getHeight());
        return w >= 0.0D && h >= 0.0D ? w * h : 0.0D;
    }

    private double overlap(double x1, double w1, double x2, double w2) {
        double l1 = x1 - w1 / 2.0D;
        double l2 = x2 - w2 / 2.0D;
        double left = Math.max(l1, l2);
        double r1 = x1 + w1 / 2.0D;
        double r2 = x2 + w2 / 2.0D;
        double right = Math.min(r1, r2);
        return right - left;
    }

    List<DetectResult> processOutput(float[] output, BufferedImage img, float rate) {
        List<DetectResult> list = new ArrayList<>();

        int sizeClasses = labels.length;
        int stride = 5 + sizeClasses;
        int size = output.length / stride;

        for (int i = 0; i < size; ++i) {
            int indexBase = i * stride;
            float maxClass = 0.0F;
            int maxIndex = 0;

            for (int c = 0; c < sizeClasses; ++c) {
                if (output[indexBase + c + 5] > maxClass) {
                    maxClass = output[indexBase + c + 5];
                    maxIndex = c;
                }
            }

            float score = maxClass * output[indexBase + 4];
            if (score > CONFIDENCE) {
                float xPos = output[indexBase];
                float yPos = output[indexBase + 1];
                float w = output[indexBase + 2];
                float h = output[indexBase + 3];
                Rectangle rect = new Rectangle(xPos / rate, yPos / rate, w / rate, h / rate);
                list.add(new DetectResult(labels[maxIndex], rect, score));
            }
        }

        return nms(list);
    }

    class DetectResult {
        char label;
        Rectangle rect;
        float confidence;

        public DetectResult(char label, Rectangle rect, float confidence) {
            this.label = label;
            this.rect = rect;
            this.confidence = confidence;
        }

        public char getLabel() {
            return label;
        }

        public void setLabel(char label) {
            this.label = label;
        }

        public Rectangle getRect() {
            return rect;
        }

        public void setRect(Rectangle rect) {
            this.rect = rect;
        }

        public float getConfidence() {
            return confidence;
        }

        public void setConfidence(float confidence) {
            this.confidence = confidence;
        }
    }

    class Rectangle {
        float x;
        float y;
        float width;
        float height;

        public Rectangle(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public float getWidth() {
            return width;
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public float getHeight() {
            return height;
        }

        public void setHeight(float height) {
            this.height = height;
        }

        @Override
        public String toString() {
            return "Rectangle{" +
                    "x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

}
