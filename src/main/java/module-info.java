open module Xiangqi {
    requires javafx.swing;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires com.sun.jna;
    requires javafx.media;
    requires com.sun.jna.platform;
    requires jnativehook;
    requires com.microsoft.onnxruntime;
    requires java.desktop;
    requires java.sql;
    requires org.java_websocket;
    requires com.google.gson;

    exports com.sojourners.chess;

}