from flask import Flask, jsonify
import os
import socket

app = Flask(__name__)

@app.route("/")
def home():
    return jsonify({
        "application": "Project Ironclad Pipeline",
        "version": "1.0.1",
        "environment": os.getenv("APP_ENV", "dev"),
        "hostname": socket.gethostname(),
        "status": "running"
    })

@app.route("/health")
def health():
    return jsonify({
        "version": "1.0.1",
        "status": "healthy"
    })

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=5000
    )