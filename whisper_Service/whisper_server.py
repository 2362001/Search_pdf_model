from flask import Flask, request, jsonify
import whisper
import tempfile
import os
import traceback

app = Flask(__name__)

# Load mô hình Whisper một lần khi server khởi động
model = whisper.load_model("base")  # bạn có thể dùng "small", "medium", hoặc "large"

@app.route('/transcribe', methods=['POST'])
def transcribe():
    try:
        if 'file' not in request.files:
            return jsonify({"error": "No file part in the request"}), 400

        file = request.files['file']
        if file.filename == '':
            return jsonify({"error": "No selected file"}), 400

        # Tạo file tạm và lưu dữ liệu vào đó
        temp = tempfile.NamedTemporaryFile(delete=False, suffix=".mp3")
        temp_path = temp.name
        temp.close()  # ĐÓNG file trước khi save để tránh lỗi PermissionError

        file.save(temp_path)

        # Chạy mô hình Whisper
        result = model.transcribe(temp_path)

        # Xóa file sau khi xử lý xong
        os.remove(temp_path)

        return jsonify({"text": result["text"]})
    
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5005)
