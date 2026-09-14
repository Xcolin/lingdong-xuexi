# 复盘中文字体

文件：NotoSansSC-VF.ttf，来自 Noto CJK 官方仓库，未修改字体数据。

- 来源：https://github.com/notofonts/noto-cjk/tree/main/Sans/Variable/TTF/Subset
- 下载地址：https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf
- SHA-256：d68bafcb48a2707749396aa12bbbd833cb70401f3a9a689fd2902c7e0d295964
- 许可：同目录 OFL.txt，来自官方 Sans/LICENSE，随应用分发。
- 用途：PDFBox 按文档实际使用字形子集嵌入，不依赖操作系统字体，不在运行时联网下载。

更新字体时须同时核对来源、哈希和许可，并重跑中文提取、分页与渲染测试。字体约 17 MB，应用打包体积增长属于显式依赖成本。
