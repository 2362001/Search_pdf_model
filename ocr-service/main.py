from flask import Flask, request, jsonify
from PIL import Image, ImageEnhance, ImageOps
import pytesseract
import io
import logging

app = Flask(__name__)

# Đường dẫn tới tesseract.exe (cài đặt trên Windows)
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

# Bật log (giúp debug khi triển khai thật)
logging.basicConfig(level=logging.INFO)

def preprocess_image(image):
    """Chuyển ảnh về grayscale, tăng độ tương phản"""
    image = image.convert('L')  # grayscale
    image = ImageOps.invert(image)  # invert màu (đôi khi giúp OCR tốt hơn)
    image = ImageEnhance.Contrast(image).enhance(2)  # tăng độ tương phản
    return image

@app.route('/read-text', methods=['POST'])
def read_text():
    if 'image' not in request.files:
        return jsonify({'error': 'No image provided'}), 400

    image_file = request.files['image']
    try:
        image_bytes = image_file.read()
        image = Image.open(io.BytesIO(image_bytes))
    except Exception as e:
        return jsonify({'error': f'Cannot open image: {str(e)}'}), 400

    # Tiền xử lý ảnh
    image = preprocess_image(image)

    # Cấu hình Tesseract
    custom_config = r'--oem 3 --psm 6'
    try:
        raw_text = pytesseract.image_to_string(image, lang='vie+eng', config=custom_config)
        
        # Làm sạch text: loại bỏ dòng rỗng, ghép lại thành một đoạn
        cleaned_text = ' '.join([line.strip() for line in raw_text.splitlines() if line.strip()])
    except Exception as e:
        return jsonify({'error': f'OCR failed: {str(e)}'}), 500

    return jsonify({'text': cleaned_text})

if __name__ == '__main__':
    app.run(debug=True)
