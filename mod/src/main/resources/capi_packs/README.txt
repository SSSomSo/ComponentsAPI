ComponentsAPI 加密资源包目录
============================

把用 config/capi/config.toml 中密钥加密后的资源包（.dat）放到本目录，
随模组 jar 一起构建（最终位于 jar 内的 capi_packs/ 路径）。
启动时客户端会在内存中解密并直接加载这些资源包，不会向磁盘（包括临时目录）
写出任何解密内容。

生成 .dat 的步骤：
1. 启动一次游戏（或客户端），模组会自动生成 config/capi/config.toml
   （内含 base64 的 512 位密钥）。
2. 把待加密的资源包 .zip 放入 config/capi/rp/。
3. 再次启动，模组会在 config/capi/rp/dat/ 生成同名 .dat。
4. 把 .dat 复制到本目录（mod/src/main/resources/capi_packs/），重新构建模组。

注意：
- 本目录只有 .dat 会被加载；本说明文件（README.txt）不参与加载。
- 加密为自定义 AES-256-GCM 容器（512 位密钥材料），不是 ZIP 自带加密，
  无法用压缩软件打开；密钥丢失后 .dat 无法解密。
- 加密后的 .dat 必须放进 jar 内才能加载，运行时不会读取 jar 外的 .dat。
