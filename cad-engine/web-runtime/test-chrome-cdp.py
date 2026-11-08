import http.server
import socketserver
import threading
import time
import subprocess
import urllib.request
import json
import websocket # if available, or use urllib / CDP

PORT = 9988
web_dir = r"F:\EW DWG View\android\app\src\main\assets\web-runtime"

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=web_dir, **kwargs)

    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        if self.path.endswith('.wasm'):
            self.send_header('Content-Type', 'application/wasm')
        super().end_headers()

    def do_GET(self):
        if self.path == '/current-drawing-stream':
            dwg_path = r"C:\Users\ThangTran\Desktop\HoSo_ThiCong_NhaCap4_Full_CD.dwg"
            with open(dwg_path, 'rb') as f:
                content = f.read()
            self.send_response(200)
            self.send_header('Content-Type', 'application/octet-stream')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Content-Length', str(len(content)))
            self.end_headers()
            self.wfile.write(content)
            return
        return super().do_GET()

httpd = socketserver.TCPServer(("", PORT), Handler)
server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
server_thread.start()
print(f"Server started at http://localhost:{PORT}")

chrome_exe = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
chrome_cmd = [
    chrome_exe,
    "--headless=new",
    "--remote-debugging-port=9222",
    "--disable-gpu",
    "--no-sandbox",
    f"http://localhost:{PORT}/index.html"
]

proc = subprocess.Popen(chrome_cmd)
time.sleep(2)

try:
    # Query Chrome debugging pages
    with urllib.request.urlopen("http://localhost:9222/json") as r:
        pages = json.loads(r.read().decode('utf-8'))
        print("Pages:", pages)
        ws_url = pages[0]['webSocketDebuggerUrl']
        print("WS URL:", ws_url)
finally:
    proc.kill()
    httpd.shutdown()
