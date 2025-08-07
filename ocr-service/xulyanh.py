from flask import Flask, request, jsonify
import google.generativeai as genai
import os
from dotenv import load_dotenv
from PIL import Image
import io

# Load API Key từ .env
load_dotenv()
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")

# Cấu hình Gemini AI
genai.configure(api_key=GOOGLE_API_KEY)
model = genai.GenerativeModel("models/gemini-pro-vision")

# Khởi tạo Flask app
app = Flask(__name__)

@app.route('/summarize-image', methods=['POST'])
def summarize_image():
    if 'image' not in request.files:
        return jsonify({'error': 'No image file uploaded'}), 400

    image_file = request.files['image']
    try:
        # Đọc file ảnh và chuyển sang định dạng PIL.Image
        image = Image.open(io.BytesIO(image_file.read()))

        # Prompt cho AI
        prompt = "Đọc văn bản trong ảnh và tóm tắt lại."

        # Gửi ảnh và prompt vào Gemini Vision
        response = model.generate_content([image, prompt])
        summarized_text = response.text.strip()

        return jsonify({
            'summary': summarized_text
        })

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(debug=True)
