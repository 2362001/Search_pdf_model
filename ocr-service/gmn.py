import google.generativeai as genai

genai.configure(api_key="AIzaSyDKaentrsmrEx2fVoR39cwedWPg2M8iO88")  # Thay bằng API key của bạn

# Dùng model đúng tên
model = genai.GenerativeModel(model_name="models/gemini-2.5-pro")

prompt = """
Viết lại đoạn văn sau cho đúng chính tả và ngữ pháp:

Tri tué nhân tao (Al) dang ngày cang trở nên phé bién trong đời sống hiện dai.
No được ứng dung rộng rai trong nhiều lĩnh vực như y té, giáo dục, tài chính, và giao thông.
Nhờ vào Al, các hệ théng có thé ty động phan tích dữ liệu va dua ra quyết định thông minh.
Cac tro ly ảo như Siri, Google Assistant hay ChatGPT déu la san phẩm của Al.
Trong y té, Al hỗ tro bac si chan đoán bệnh nhanh hon va chính xac hon.
Trong giao duc, Al giúp ca nhân hóa viéc hoc cho từng hoc sinh dua trén kha nang riêng.
Nhiều ngân hang da dung Al dé phat hiện gian lan va bao vệ thông tin khach hang.
Cac xe ty lai cũng la một thanh tựu nổi bat của công nghệ trí tuệ nhân tạo.
Tuy nhiên, AI cũng đặt ra nhiều thách thức về đạo đức và quyền riéng tu.
Do do, việc phat trién Al can di kèm voi cdc quy định va giám sat phù hop.
"""

response = model.generate_content(prompt)
print(response.text)
