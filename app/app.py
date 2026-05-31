from flask import Flask, jsonify
import os
import socket

app = Flask(__name__)

@app.route("/")
def home():
    return jsonify({
        "application": "Project Ironclad Pipeline",
        "environment": os.getenv("APP_ENV", "dev"),
        "hostname": socket.gethostname(),
        "status": "running"
    })

@app.route("/health")
def health():
    return jsonify({
        "status": "healthy"
    })

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5000
    )