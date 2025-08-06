from flask import Flask, request, jsonify
from PIL import Image, ImageEnhance, ImageOps
import pytesseract
import io
import logging
import os
import google.generativeai as genai
from dotenv import load_dotenv

# Load biến môi trường từ file .env
load_dotenv()
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")

# Cấu hình Flask
app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# Cấu hình đường dẫn tới Tesseract (Windows)
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

# Cấu hình Google Gemini AI
genai.configure(api_key=GOOGLE_API_KEY)
model = genai.GenerativeModel('models/gemini-2.5-pro')  # Hoặc 'gemini-pro'

def preprocess_image(image):
    """Tiền xử lý ảnh: grayscale + tăng tương phản"""
    logging.info("Tiền xử lý ảnh...")
    image = image.convert('L')  # grayscale
    image = ImageOps.invert(image)
    image = ImageEnhance.Contrast(image).enhance(2)
    return image

def refine_text_with_ai(text):
    """Dùng Gemini AI để hiệu chỉnh văn bản"""
    try:
        prompt = f"""Hãy giúp tôi hiệu chỉnh đoạn văn dưới đây sao cho chuẩn ngữ pháp, chính tả, giữ nguyên nghĩa và rõ ràng hơn. Trả về duy nhất văn bản đã được chỉnh sửa:

{text}
"""
        response = model.generate_content(prompt)
        return response.text.strip()
    except Exception as e:
        logging.error(f"Lỗi khi gọi AI: {e}")
        return None

@app.route('/read-text', methods=['POST'])
def read_text():
    if 'image' not in request.files:
        logging.warning("Không có ảnh trong request.")
        return jsonify({'error': 'No image provided'}), 400

    image_file = request.files['image']
    try:
        image_bytes = image_file.read()
        image = Image.open(io.BytesIO(image_bytes))
        logging.info("Đã đọc ảnh thành công.")
    except Exception as e:
        logging.error(f"Lỗi mở ảnh: {str(e)}")
        return jsonify({'error': f'Cannot open image: {str(e)}'}), 400

    image = preprocess_image(image)

    custom_config = r'--oem 3 --psm 6'
    try:
        raw_text = pytesseract.image_to_string(image, lang='vie+eng', config=custom_config)
        cleaned_text = ' '.join([line.strip() for line in raw_text.splitlines() if line.strip()])
        logging.info("Đã OCR xong văn bản.")

        # Dùng AI để hiệu chỉnh
        ai_corrected_text = refine_text_with_ai(cleaned_text)
        if not ai_corrected_text:
            return jsonify({'error': 'AI failed to refine text'}), 500

    except Exception as e:
        logging.error(f"OCR hoặc AI thất bại: {str(e)}")
        return jsonify({'error': f'Processing failed: {str(e)}'}), 500

    logging.info("Trả kết quả thành công.")
    return jsonify({
        'raw_text': cleaned_text,
        'ai_corrected_text': ai_corrected_text
    })

if __name__ == '__main__':
    logging.info("Bắt đầu chạy server Flask...")
    app.run(debug=True)
