import urllib.request
import json
import time
import os
import zipfile

run_id = 42028566805
url = f"https://api.github.com/repos/khanhvy2022/eurowindow-cad-viewer/actions/runs/{run_id}"
headers = {"User-Agent": "Mozilla/5.0"}

print(f"Monitoring GitHub Actions Run {run_id}...")

start_time = time.time()
while time.time() - start_time < 300:
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            status = data.get("status")
            conclusion = data.get("conclusion")
            print(f"[{int(time.time() - start_time)}s] Status: {status}, Conclusion: {conclusion}")
            if status == "completed":
                if conclusion == "success":
                    print("Build succeeded!")
                    break
                else:
                    print("Build failed with conclusion:", conclusion)
                    exit(1)
    except Exception as e:
        print("Poll error:", e)
    time.sleep(10)
else:
    print("Timed out waiting for build")
    exit(1)

# Now check release assets on tag v1.0.6
release_url = "https://api.github.com/repos/khanhvy2022/eurowindow-cad-viewer/releases/tags/v1.0.6"
print("Checking GitHub Release for v1.0.6...")
for attempt in range(15):
    try:
        req = urllib.request.Request(release_url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            rel = json.loads(resp.read().decode('utf-8'))
            assets = rel.get("assets", [])
            print(f"Found {len(assets)} release assets.")
            for a in assets:
                name = a.get("name")
                download_url = a.get("browser_download_url")
                print(f" - Asset: {name} -> {download_url}")
                if "release.apk" in name:
                    out_path = os.path.join(r"F:\EW DWG View\release\debug", "com.eurowindow.cadviewer-v1.0.6-release.apk")
                    print(f"Downloading {name} to {out_path}...")
                    urllib.request.urlretrieve(download_url, out_path)
                    print("Downloaded successfully! Size:", os.path.getsize(out_path))
                    exit(0)
    except Exception as e:
        print(f"Release not ready yet (attempt {attempt+1}/15):", e)
    time.sleep(5)
