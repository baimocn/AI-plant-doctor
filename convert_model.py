import torch
import torch.nn as nn
from torchvision import models
import os

def convert_to_torchscript(model_path, output_path, num_classes=134):
    """
    将PyTorch模型转换为TorchScript格式用于Android部署
    """
    print(f"正在加载模型: {model_path}")
    
    # 创建模型结构
    model = models.resnet50(pretrained=False)
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, num_classes)
    
    # 加载模型权重
    device = torch.device('cpu')  # 使用CPU转换
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()
    
    # 创建示例输入
    example_input = torch.randn(1, 3, 224, 224)
    
    # 转换为TorchScript
    print("正在转换为TorchScript...")
    traced_model = torch.jit.trace(model, example_input)
    
    # 优化模型用于移动端
    print("正在优化模型...")
    scripted_model = traced_model
    
    # 保存模型
    scripted_model.save(output_path)
    print(f"模型已保存到: {output_path}")
    
    print("\n模型转换成功!")
    print(f"请将生成的 {output_path} 复制到 android/app/src/main/assets/ 目录下")

def prepare_assets():
    """
    准备Android应用所需的assets文件
    """
    assets_dir = "android/app/src/main/assets"
    os.makedirs(assets_dir, exist_ok=True)
    
    # 复制类别文件
    if os.path.exists("class_names_134.txt"):
        import shutil
        shutil.copy("class_names_134.txt", os.path.join(assets_dir, "class_names_134.txt"))
        print("已复制 class_names_134.txt 到 assets")
    
    # 复制病害数据
    if os.path.exists("disease_data.json"):
        import shutil
        shutil.copy("disease_data.json", os.path.join(assets_dir, "disease_data.json"))
        print("已复制 disease_data.json 到 assets")
    
    print(f"\n请确保模型文件 model.ptl 也放置在 {assets_dir} 目录下")

if __name__ == "__main__":
    model_file = "best_model_134.pth"
    output_file = "android/app/src/main/assets/model.ptl"
    
    if os.path.exists(model_file):
        convert_to_torchscript(model_file, output_file)
        prepare_assets()
    else:
        print(f"错误: 找不到模型文件 {model_file}")
        print("请先训练模型或确保模型文件在正确位置")
        prepare_assets()
