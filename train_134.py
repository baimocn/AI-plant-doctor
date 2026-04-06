import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
from torch.utils.data import DataLoader, WeightedRandomSampler
import os
import time
from tqdm import tqdm

# ==================== 配置 ====================
DATA_ROOT = './data'   # 修改为你的实际路径
BATCH_SIZE = 64          # 根据显存调整，GPU建议64，CPU/核显用16
NUM_EPOCHS = 40
LEARNING_RATE = 0.001
NUM_WORKERS = 4          # Linux下可以设大一点
USE_WEIGHTED_SAMPLER = True   # 处理类别不平衡
IMAGE_SIZE = 224
MODEL_NAME = 'resnet50'  # 可选：resnet50, efficientnet_b3
# =============================================

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

# 数据预处理
train_transform = transforms.Compose([
    transforms.RandomResizedCrop(IMAGE_SIZE),
    transforms.RandomHorizontalFlip(),
    transforms.RandomRotation(15),
    transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])

val_transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(IMAGE_SIZE),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])

# 加载数据集
train_dataset = datasets.ImageFolder(os.path.join(DATA_ROOT, 'train'), train_transform)
val_dataset = datasets.ImageFolder(os.path.join(DATA_ROOT, 'val'), val_transform)

class_names = train_dataset.classes
num_classes = len(class_names)
print(f"Number of classes: {num_classes}")
# 保存类别列表到文件，供app.py使用
with open('class_names_134.txt', 'w') as f:
    for name in class_names:
        f.write(name + '\n')

# 处理类别不平衡
if USE_WEIGHTED_SAMPLER:
    class_counts = [0] * num_classes
    for _, label in train_dataset.samples:
        class_counts[label] += 1
    class_weights = 1.0 / torch.tensor(class_counts, dtype=torch.float)
    sample_weights = [class_weights[label] for _, label in train_dataset.samples]
    sampler = WeightedRandomSampler(sample_weights, len(sample_weights))
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, sampler=sampler, num_workers=NUM_WORKERS)
else:
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, num_workers=NUM_WORKERS)

val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, num_workers=NUM_WORKERS)

# 加载模型
if MODEL_NAME == 'resnet50':
    model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V1)
    num_ftrs = model.fc.in_features
    model.fc = nn.Linear(num_ftrs, num_classes)
elif MODEL_NAME == 'efficientnet_b3':
    import timm
    model = timm.create_model('efficientnet_b3', pretrained=True, num_classes=num_classes)
else:
    raise ValueError("Unsupported model")

model = model.to(device)

criterion = nn.CrossEntropyLoss()
optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)
scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode='min', patience=5, factor=0.5)

# 训练循环
best_acc = 0.0
for epoch in range(NUM_EPOCHS):
    print(f"\nEpoch {epoch+1}/{NUM_EPOCHS}")
    print("-" * 30)
    
    model.train()
    running_loss = 0.0
    running_corrects = 0
    
    for inputs, labels in tqdm(train_loader, desc="Train"):
        inputs, labels = inputs.to(device), labels.to(device)
        optimizer.zero_grad()
        outputs = model(inputs)
        _, preds = torch.max(outputs, 1)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()
        
        running_loss += loss.item() * inputs.size(0)
        running_corrects += torch.sum(preds == labels.data)
    
    epoch_loss = running_loss / len(train_dataset)
    epoch_acc = running_corrects.double() / len(train_dataset)
    print(f"Train Loss: {epoch_loss:.4f} Acc: {epoch_acc:.4f}")
    
    # 验证
    model.eval()
    val_loss = 0.0
    val_corrects = 0
    with torch.no_grad():
        for inputs, labels in tqdm(val_loader, desc="Val"):
            inputs, labels = inputs.to(device), labels.to(device)
            outputs = model(inputs)
            _, preds = torch.max(outputs, 1)
            loss = criterion(outputs, labels)
            val_loss += loss.item() * inputs.size(0)
            val_corrects += torch.sum(preds == labels.data)
    
    val_loss = val_loss / len(val_dataset)
    val_acc = val_corrects.double() / len(val_dataset)
    print(f"Val Loss: {val_loss:.4f} Acc: {val_acc:.4f}")
    
    scheduler.step(val_loss)
    
    if val_acc > best_acc:
        best_acc = val_acc
        torch.save(model.state_dict(), 'best_model_134.pth')
        print(f"  -> Saved best model (acc={best_acc:.4f})")

print(f"\nTraining finished. Best val accuracy: {best_acc:.4f}")
torch.save(model.state_dict(), 'final_model_134.pth')