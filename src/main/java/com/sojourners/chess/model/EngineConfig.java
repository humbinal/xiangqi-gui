package com.sojourners.chess.model;

import com.sojourners.chess.enginee.Engine;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class EngineConfig implements Serializable {

    private static final long serialVersionUID = 1323134234;

    private Engine.Type type;

    private String name;

    private String path;

    private String protocol;

    private LinkedHashMap<String, String> options;

    public EngineConfig(Engine.Type type, String name, String path, String protocol, LinkedHashMap<String, String> options) {
        this.type = type;
        this.name = name;
        this.path = path;
        this.protocol = protocol;
        this.options = options;
    }

    public Engine.Type getType() {
        return type;
    }

    public void setType(Engine.Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public LinkedHashMap<String, String> getOptions() {
        return options;
    }

    public void setOptions(LinkedHashMap<String, String> options) {
        this.options = options;
    }

}
