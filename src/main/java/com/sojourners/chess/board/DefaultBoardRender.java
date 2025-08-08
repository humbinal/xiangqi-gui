package com.sojourners.chess.board;

import com.sojourners.chess.util.XiangqiUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class DefaultBoardRender extends BaseBoardRender {

    private Image bgImage;
    private Font font;
    private int fontSize;

    public DefaultBoardRender(Canvas canvas) {
        super(canvas);
        this.bgImage = new Image(ChessBoard.class.getResourceAsStream("/image/BOARD.JPG"));
    }

    @Override
    public void drawBackgroundImage(double width, double height) {
        gc.drawImage(bgImage, 0, 0, width, height);
    }

    @Override
    public Color getBackgroundColor() {
        int centerX = (int) (bgImage.getWidth() / 2);
        int centerY = (int) (bgImage.getHeight() / 2);
        return this.bgImage.getPixelReader().getColor(centerX, centerY);
    }


    @Override
    public void drawPieces(int pos, int piece, char[][] board, boolean isReverse, ChessBoard.BoardSize style) {
        if (font == null || fontSize != getFontSize(style)) {
            fontSize = getFontSize(style);
            font = Font.loadFont(getClass().getResourceAsStream("/font/chessman.ttf"), fontSize);
        }
        // 绘制棋子
        int r = (piece - piece / 10) / 2;
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                String word = ChessBoard.map.get(board[i][j]);
                if (word != null) {
                    int x = pos + piece * getReverseX(j, isReverse);
                    int y = pos + piece * getReverseY(i, isReverse);

                    double bW = getPieceBw(style);
                    double sW = getPieceSw(style);

                    Color color = Color.web(XiangqiUtils.isRed(board[i][j]) ? "#AD1A02" : "#167B7F");
                    gc.setFill(Color.WHITE);
                    gc.fillOval(x - r, y - r, 2 * r, 2 * r);
                    gc.setStroke(color);
                    gc.setLineWidth(bW);
                    gc.strokeOval(x - r, y - r, 2 * r, 2 * r);
                    gc.setLineWidth(sW);
                    gc.strokeOval(x - r + bW * 1.8, y - r + bW * 1.8, 2 * (r - bW * 1.8), 2 * (r - bW * 1.8));
                    gc.setFill(color);
                    gc.setFont(font);
                    gc.fillText(word, x - fontSize / 2, y + fontSize / 2 - fontSize / 5.5);
                }
            }
        }
    }

    /**
     * 棋子外圈线条宽度
     *
     * @return
     */
    private double getPieceBw(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 16d;
    }

    /**
     * 棋子内圈线条宽度
     *
     * @return
     */
    private double getPieceSw(ChessBoard.BoardSize style) {
        return getPieceBw(style) / 4d;
    }

    private int getFontSize(ChessBoard.BoardSize style) {
        return getPieceSize(style) / 2;
    }
}
