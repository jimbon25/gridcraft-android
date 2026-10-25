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

        function getCanvas() {
          return document.querySelector('canvas') || document.getElementById('canvas');
        }
        function getInput() {
          return document.querySelector('input');
        }

        // Lock inputmode to "none" so Chromium Android NEVER pops up the virtual keyboard on touch/scroll/selection
        function lockInputMode() {
          const inp = getInput();
          if (inp && !window.__softKeyboardRequested) {
            if (inp.getAttribute('inputmode') !== 'none') {
              inp.setAttribute('inputmode', 'none');
            }
          }
        }

        const observer = new MutationObserver(function() {
          lockInputMode();
        });
        observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
        setInterval(lockInputMode, 200);

        window.__showKeyboard = function() {
          window.__softKeyboardRequested = true;
          const cvs = getCanvas();
          if (cvs) {
            try { cvs.focus({ preventScroll: true }); } catch (e) {}
            // Send F2 keydown directly to canvas to enter in-cell editing mode
            const ev = new KeyboardEvent('keydown', {
              key: 'F2',
              code: 'F2',
              keyCode: 113,
              which: 113,
              bubbles: true,
              cancelable: true
            });
            cvs.dispatchEvent(ev);
          }
          const inp = getInput();
          if (inp) {
            inp.setAttribute('inputmode', 'text');
            try { inp.focus({ preventScroll: true }); } catch (e) { inp.focus(); }
          }
        };

        window.__hideKeyboard = function() {
          window.__softKeyboardRequested = false;
          const inp = getInput();
          if (inp) {
            inp.setAttribute('inputmode', 'none');
            try { inp.blur(); } catch (e) {}
          }
          const cvs = getCanvas();
          if (cvs) {
            try { cvs.focus({ preventScroll: true }); } catch (e) {}
          }
        };

        window.__onKeyboardHidden = function() {
          window.__softKeyboardRequested = false;
          const inp = getInput();
          if (inp) {
            inp.setAttribute('inputmode', 'none');
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
            if (window.__showKeyboard) window.__showKeyboard();
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
