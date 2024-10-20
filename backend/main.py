import os
import zipfile
from flask import Flask, request

app = Flask(__name__)

def unzip(file, dst):
    if zipfile.is_zipfile(file):
        fz = zipfile.ZipFile(file, 'r')
        for f in fz.namelist():
            fz.extract(f, dst)

@app.route("/record", methods=["POST"])
def upload_file():
    file = request.files["file"]
    path = request.form['path']
    folder = os.path.join('local', '.' + path)
    os.makedirs(folder, exist_ok=True)
    if file:
        zip_path = os.path.join(folder, file.filename)
        file.save(zip_path)
        print(zip_path)
        if zipfile.is_zipfile(zip_path):
            unzip(zip_path, 'local')
    return {}

if __name__ == '__main__':
    os.makedirs('local', exist_ok=True)
    app.run(port=7777, host="0.0.0.0")
