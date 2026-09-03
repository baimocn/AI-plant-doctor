# AI-Plant-Doctor 植物病害识别系统

基于深度学习（ResNet50）的植物叶片病害识别 Web 应用。

## 功能
- 上传植物叶片图片，自动识别病害类别（支持 134 类）
- 返回病害识别结果与对应信息（来自 disease_data.json）
- 图片自动压缩处理（限制 1MB 上传，压缩至 320x320 保障传输效率）

## 技术栈
- Python + Flask（Web 后端）
- PyTorch（ResNet50 迁移学习，JIT 加速推理）
- Flask-CORS（跨域支持）

## 快速开始
\\\ash
pip install -r requirements.txt
python app.py
\\\
启动后访问本地服务，上传叶片图片即可识别。

## 项目结构
- \pp.py\ — Flask 应用入口（模型加载 + 预测接口）
- \	rain_134.py\ — 134 类病害分类训练脚本
- \class_names_134.txt\ — 类别名称表
- \disease_data.json\ — 病害信息数据（症状/防治建议等）
- \	est134.html\ — 测试页面

## License
MIT

© 2026 baimocn