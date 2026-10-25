#!/usr/bin/env python3
import sys

BRIDGE_SNIPPET = """  <script>
    // AndroidBridge file save integration
    (function() {
      function setupBridge() {
        if (!window.AndroidBridge) return;
        const origClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function() {
          if (this.download && this.href && this.href.startsWith("blob:")) {
            const filename = this.download;
            fetch(this.href)
              .then(res => res.blob())
              .then(blob => {
                const reader = new FileReader();
                reader.onloadend = function() {
                  const base64 = reader.result.split(',')[1];
                  window.AndroidBridge.saveFile(base64, filename, blob.type || "application/octet-stream");
                };
                reader.readAsDataURL(blob);
              })
              .catch(err => console.error("Error in AndroidBridge download:", err));
            return;
          }
          return origClick.apply(this, arguments);
        };
      }
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupBridge);
      } else {
        setupBridge();
      }
    })();
  </script>"""

def main():
    if len(sys.argv) < 2:
        print("Usage: patch_index.py <path/to/index.html>")
        sys.exit(1)
    path = sys.argv[1]
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if "AndroidBridge file save integration" in content:
        print("Already patched")
        return
    if "</head>" in content:
        content = content.replace("</head>", BRIDGE_SNIPPET + "\n</head>")
    else:
        content = BRIDGE_SNIPPET + "\n" + content
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Successfully patched index.html with AndroidBridge")

if __name__ == "__main__":
    main()
