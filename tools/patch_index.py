#!/usr/bin/env python3
import sys

BRIDGE_SNIPPET = """  <script>
    // AndroidBridge file save & virtual keyboard integration
    (function() {
      function setupBridge() {
        if (!window.AndroidBridge) return;

        // 1. Intercept file downloads via blob links
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

        // 2. Prepare keyboard helper (F2 enters cell edit mode even on blank/new workbook cells)
        window.__prepareKeyboard = function() {
          window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F2', code: 'F2', bubbles: true }));
          const inp = document.querySelector('input');
          if (inp) {
            try { inp.focus({ preventScroll: true }); } catch (err) { inp.focus(); }
          }
        };

        // 3. Stationary double-tap only (ignores scroll and range-selection drag)
        let touchStartX = 0;
        let touchStartY = 0;
        let touchMoved = false;
        let lastTapTime = 0;

        window.addEventListener('touchstart', function(e) {
          if (e.touches.length === 1) {
            touchStartX = e.touches[0].clientX;
            touchStartY = e.touches[0].clientY;
            touchMoved = false;
          }
        }, { passive: true });

        window.addEventListener('touchmove', function(e) {
          if (e.touches.length > 0) {
            const dx = Math.abs(e.touches[0].clientX - touchStartX);
            const dy = Math.abs(e.touches[0].clientY - touchStartY);
            if (dx > 10 || dy > 10) {
              touchMoved = true;
            }
          }
        }, { passive: true });

        window.addEventListener('touchend', function(e) {
          if (touchMoved) return; // Ignore drag / scroll / selection
          const now = Date.now();
          if (now - lastTapTime < 350) {
            if (window.__prepareKeyboard) window.__prepareKeyboard();
            if (window.AndroidBridge && window.AndroidBridge.showKeyboard) {
              window.AndroidBridge.showKeyboard();
            }
          }
          lastTapTime = now;
        }, { passive: true });
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
    if "AndroidBridge file save & virtual keyboard integration" in content:
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
