from flask import Flask, request, jsonify
import whisper
import os
import tempfile

app = Flask(__name__)

# Load model once when server starts
model = whisper.load_model("large")

@app.route("/transcribe", methods=["POST"])
def transcribe_audio():
    if 'file' not in request.files:
        return jsonify({"error": "No file provided"}), 400

    audio_file = request.files['file']

    if audio_file.filename == '':
        return jsonify({"error": "Empty filename"}), 400

    # Save to temp file and make sure it's closed before use
    try:
        temp_fd, temp_path = tempfile.mkstemp(suffix=".mp3")
        os.close(temp_fd)  # Close the file descriptor to avoid Windows locking issues
        audio_file.save(temp_path)

        result = model.transcribe(temp_path,language='vi')
        text = result['text']
        return jsonify({"text": text})

    except Exception as e:
        return jsonify({"error": str(e)}), 500

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
