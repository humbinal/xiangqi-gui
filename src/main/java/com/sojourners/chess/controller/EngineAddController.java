package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.util.PathUtils;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class EngineAddController {

    private Properties prop;

    @FXML
    private TextField pathText;

    @FXML
    private TextField nameText;

    @FXML
    private TextField protocolText;

    @FXML
    private ListView<Map.Entry<String, String>> optionsListView;

    @FXML
    private RadioButton localEngineRadio;

    @FXML
    private RadioButton remoteEngineRadio;

    @FXML
    private ToggleGroup engineTypeGroup;

    @FXML
    private Button selectButton;

    @FXML
    private Button connectButton;

    public static EngineConfig ec;

    private LinkedHashMap<String, String> options;

    @FXML
    void selectButtonClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(PathUtils.getJarPath()));
        File file = fileChooser.showOpenDialog(App.getEngineAdd());
        if (file != null) {
            processEngineFile(file.getPath(), file.getName());
        }
    }

    @FXML
    void connectButtonClick(ActionEvent e) {
        String path = pathText.getText().trim();
        if (path.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText("请输入远程引擎URL，如：localhost:8080");
            alert.showAndWait();
            return;
        }

        // 对于远程引擎，使用路径作为名称（如果没有输入名称的话）
        String engineName = nameText.getText().trim();
        if (engineName.isEmpty()) {
            engineName = path;
        }

        processEngineFile(path, engineName);
    }

    private void processEngineFile(String path, String name) {
        pathText.setText(path);
        if (nameText.getText().trim().isEmpty()) {
            nameText.setText(name);
        }
        Engine.Type type = isLocalEngine() ? Engine.Type.LOCAL : Engine.Type.REMOTE;
        String protocol = Engine.test(type, path, options = new LinkedHashMap<>());
        if (protocol == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("提示");
            alert.setHeaderText("无效的引擎文件或连接失败");
            alert.showAndWait();
            return;
        }
        protocolText.setText(protocol);
        showOptions();
    }

    private void showOptions() {
        optionsListView.getItems().clear();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            optionsListView.getItems().add(entry);
        }
    }

    @FXML
    void cancelButtonClick(ActionEvent event) {
        App.closeEngineAdd();
    }

    @FXML
    void okButtonClick(ActionEvent event) {
        System.out.println("isLocalEngine:" + isLocalEngine());
        Engine.Type engineType = isLocalEngine() ? Engine.Type.LOCAL : Engine.Type.REMOTE;
        String protocol = protocolText.getText();
        if (!"uci".equals(protocol) && !"ucci".equals(protocol)) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("提示");
            alert.setHeaderText("引擎协议不正确");
            alert.showAndWait();
            return;
        }

        String path = pathText.getText().trim();
        String name = nameText.getText().trim();

        if (path.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText("请输入引擎路径");
            alert.showAndWait();
            return;
        }

        if (name.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText("请输入引擎名称");
            alert.showAndWait();
            return;
        }

        if (ec == null) {
            // 添加引擎
            prop.getEngineConfigList().add(new EngineConfig(engineType, nameText.getText(), pathText.getText(), protocolText.getText(), options));
        } else {
            // 编辑引擎
            ec.setType(engineType);
            ec.setName(nameText.getText());
            ec.setPath(pathText.getText());
            ec.setProtocol(protocolText.getText());
            ec.setOptions(options);
        }
        App.closeEngineAdd();
    }

    public void initialize() {
        prop = Properties.getInstance();

        initListView();

        // 初始化引擎类型选择监听器
        setupEngineTypeListener();

        if (ec != null) {
            nameText.setText(ec.getName());
            pathText.setText(ec.getPath());
            protocolText.setText(ec.getProtocol());

            this.options = (LinkedHashMap<String, String>) ec.getOptions().clone();
            showOptions();

            if (ec.getType().equals(Engine.Type.LOCAL)) {
                localEngineRadio.setSelected(true);
            } else {
                remoteEngineRadio.setSelected(true);
            }
        } else {
            // 默认选择本地引擎
            localEngineRadio.setSelected(true);
        }

        // 更新UI状态
        updateUIState();
    }

    private void setupEngineTypeListener() {
        localEngineRadio.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateUIState();
        });

        remoteEngineRadio.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateUIState();
        });
    }

    private void updateUIState() {
        boolean isLocal = localEngineRadio.isSelected();
        // 控制按钮显示
        // 本地引擎
        selectButton.setVisible(isLocal);
        selectButton.setManaged(isLocal);
        // 远程引擎
        connectButton.setVisible(!isLocal);
        connectButton.setManaged(!isLocal);
        // 控制按钮启用状态
        selectButton.setDisable(!isLocal);
    }

    private void initListView() {
        optionsListView.setSelectionModel(new MultipleSelectionModel<>() {
            private ObservableList emptyList = FXCollections.emptyObservableList();

            @Override
            public ObservableList<Integer> getSelectedIndices() {
                return emptyList;
            }

            @Override
            public ObservableList<Map.Entry<String, String>> getSelectedItems() {
                return emptyList;
            }

            @Override
            public void selectIndices(int i, int... ints) {

            }

            @Override
            public void selectAll() {

            }

            @Override
            public void selectFirst() {

            }

            @Override
            public void selectLast() {

            }

            @Override
            public void clearAndSelect(int i) {

            }

            @Override
            public void select(int i) {

            }

            @Override
            public void select(Map.Entry<String, String> stringStringEntry) {

            }

            @Override
            public void clearSelection(int i) {

            }

            @Override
            public void clearSelection() {

            }

            @Override
            public boolean isSelected(int i) {
                return false;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public void selectPrevious() {

            }

            @Override
            public void selectNext() {

            }
        });
        optionsListView.setCellFactory(new Callback() {
            @Override
            public Object call(Object param) {
                ListCell<Map.Entry<String, String>> cell = new ListCell<>() {
                    @Override
                    protected void updateItem(Map.Entry<String, String> item, boolean bln) {
                        super.updateItem(item, bln);
                        if (!bln) {
                            HBox box = new HBox();

                            Label label = new Label();
                            label.setText(item.getKey());
                            label.setAlignment(Pos.CENTER_LEFT);
                            label.setPrefHeight(27);
                            label.setPrefWidth(100);
                            box.getChildren().add(label);

                            TextField input = new TextField();
                            input.setText(item.getValue());
                            input.setPrefWidth(120);
                            input.textProperty().addListener(new ChangeListener<String>() {
                                @Override
                                public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                                    options.put(item.getKey(), t1);
                                }
                            });
                            box.getChildren().add(input);

                            setGraphic(box);
                        }
                    }
                };
                return cell;
            }

        });
    }

    // 获取当前引擎类型
    public boolean isLocalEngine() {
        return localEngineRadio.isSelected();
    }

}