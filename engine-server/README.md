# xaingqi-engine-server

将UCI引擎服务化，可实现将引擎部署在高性能服务器上，象棋GUI客户端通过websocket协议实现调用远程引擎，实现引擎计算和展示GUI设备分离。

目前支持的引擎为[Pikafish](https://github.com/official-pikafish/Pikafish)。

## 如何使用

-下载pikafish引擎发布文件，
例如： https://github.com/official-pikafish/Pikafish/releases/download/Pikafish-2025-06-23/Pikafish.2025-06-23.7z

- 解压引擎包后目录如下：
    ```
    /path/to/Pikafish.2025-06-23
    ├── Android
    │     ├── pikafish-armv8
    │     └── pikafish-armv8-dotprod
    ├── Linux
    │     ├── pikafish-avx2
    │     ├── pikafish-avx512
    │     ├── pikafish-avxvnni
    │     ├── pikafish-bmi2
    │     ├── pikafish-bw512
    │     ├── pikafish-sse41-popcnt
    │     └── pikafish-vnni512
    ├── MacOS
    │     └── pikafish-apple-silicon
    ├── Wiki
    │     ├── Advanced-topics.md
    │     ├── Compiling-from-source.md
    │     ├── Developers.md
    │     ├── Download-and-usage.md
    │     ├── Home.md
    │     ├── Pikafish-FAQ.md
    │     ├── Terminology.md
    │     ├── UCI-&-Commands.md
    │     └── _Footer.md
    ├── Windows
    │     ├── pikafish-avx2.exe
    │     ├── pikafish-avx512.exe
    │     ├── pikafish-avxvnni.exe
    │     ├── pikafish-bmi2.exe
    │     ├── pikafish-bw512.exe
    │     ├── pikafish-sse41-popcnt.exe
    │     └── pikafish-vnni512.exe
    ├── AUTHORS
    ├── CONTRIBUTING.md
    ├── Copying.txt
    ├── NNUE-License.md
    ├── README.md
    ├── Top CPU Contributors.txt
    └── pikafish.nnue
    ```
- 启动引擎服务
```
xq-engine --pikafish /path/to/Pikafish.2025-06-23
```

