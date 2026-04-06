import torch
import torch.nn as nn
from torchvision import transforms, models
from PIL import Image
import io
import os
import json
import time
import re
from flask import Flask, request, jsonify
from flask_cors import CORS

# ========== 配置 ==========
MODEL_PATH = 'best_model_134.pth'
CLASS_NAMES_FILE = 'class_names_134.txt'
DISEASE_DATA_FILE = 'disease_data.json'
IMAGE_SIZE = 224
MAX_IMAGE_SIZE_MB = 1.0
COMPRESS_TARGET_SIZE = (320, 320)
COMPRESS_QUALITY = 65
# =========================

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

# 加载类别名
with open(CLASS_NAMES_FILE, 'r') as f:
    class_names = [line.strip() for line in f.readlines()]
num_classes = len(class_names)
print(f"Loaded {num_classes} classes")

# 加载模型
model = models.resnet50(pretrained=False)
num_ftrs = model.fc.in_features
model.fc = nn.Linear(num_ftrs, num_classes)
model.load_state_dict(torch.load(MODEL_PATH, map_location=device))
model.to(device)
model.eval()

# JIT 加速
try:
    dummy_input = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE).to(device)
    model = torch.jit.trace(model, dummy_input)
    print("Model JIT traced")
except Exception as e:
    print(f"JIT skip: {e}")

# 预处理
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(IMAGE_SIZE),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])

# 加载病害信息，并清理键名中的不可见字符
if os.path.exists(DISEASE_DATA_FILE):
    with open(DISEASE_DATA_FILE, 'r', encoding='utf-8-sig') as f:
        raw_db = json.load(f)
    disease_db = {}
    for k, v in raw_db.items():
        clean_key = re.sub(r'[\u200b\u200c\u200d\u2060\ufeff]', '', k).strip()
        disease_db[clean_key] = v
    print(f"Loaded disease info for {len(disease_db)} diseases")
    sample_keys = list(disease_db.keys())[:5]
    print(f"Sample keys (repr): {[repr(k) for k in sample_keys]}")
else:
    disease_db = {}
    print("Warning: disease_data.json not found")

def compress_image_if_needed(image_bytes):
    size_mb = len(image_bytes) / (1024 * 1024)
    if size_mb <= MAX_IMAGE_SIZE_MB:
        return image_bytes
    img = Image.open(io.BytesIO(image_bytes))
    if img.mode != 'RGB':
        img = img.convert('RGB')
    img.thumbnail(COMPRESS_TARGET_SIZE, Image.LANCZOS)
    output = io.BytesIO()
    img.save(output, format='JPEG', quality=COMPRESS_QUALITY, optimize=True)
    print(f"Compressed: {size_mb:.2f}MB -> {len(output.getvalue())/(1024*1024):.2f}MB")
    return output.getvalue()

def predict_image_full(image_bytes):
    img = Image.open(io.BytesIO(image_bytes)).convert('RGB')
    tensor = transform(img).unsqueeze(0).to(device)
    with torch.no_grad():
        outputs = model(tensor)
        probs = torch.nn.functional.softmax(outputs, dim=1)
        confidence, predicted_idx = torch.max(probs, 1)
    return class_names[predicted_idx.item()], confidence.item()

def normalize_key(key):
    """标准化键名：去除不可见字符，并将连续多个下划线替换为单下划线"""
    key = re.sub(r'[\u200b\u200c\u200d\u2060\ufeff]', '', key).strip()
    key = re.sub(r'_+', '_', key)  # 多个下划线 -> 一个下划线
    return key

app = Flask(__name__)
CORS(app)

@app.route('/predict', methods=['POST'])
def predict():
    start_total = time.time()
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'Empty filename'}), 400
    try:
        image_bytes = file.read()
        image_bytes = compress_image_if_needed(image_bytes)
        pred_key_raw, conf = predict_image_full(image_bytes)
        pred_key = normalize_key(pred_key_raw)
        
        # 查找详细信息
        info = disease_db.get(pred_key)
        if not info:
            # 尝试将下划线替换为空格
            alt_key = pred_key.replace('_', ' ')
            info = disease_db.get(alt_key)
        if not info:
            # 忽略大小写
            pred_lower = pred_key.lower()
            for db_key in disease_db:
                if db_key.lower() == pred_lower:
                    info = disease_db[db_key]
                    break
        
        result = {
            'prediction': pred_key,
            'confidence': conf,
            'disease_info': info if info else {}
        }
        total_time = (time.time() - start_total) * 1000
        print(f"Total: {total_time:.0f}ms, Confidence: {conf:.3f}, Key: {pred_key}, Found: {bool(info)}")
        return jsonify(result)
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/disease_info', methods=['GET'])
def disease_info():
    key = request.args.get('key')
    if not key:
        return jsonify(disease_db)
    clean_key = normalize_key(key)
    info = disease_db.get(clean_key)
    if info:
        return jsonify(info)
    else:
        return jsonify({'error': f'No info for {key}'}), 404

@app.route('/')
def index():
    return "Plant Disease Recognition API (with confidence & full info). Use /predict (POST)."

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)