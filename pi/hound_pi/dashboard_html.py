def get_dashboard_html() -> str:
    return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>HOUND Rover - AP Control & 2D Vision Map</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg-dark: #0b0f19;
      --card-bg: rgba(22, 28, 45, 0.75);
      --card-border: rgba(255, 255, 255, 0.1);
      --accent-cyan: #00f2fe;
      --accent-purple: #9d4edd;
      --accent-pink: #ff007f;
      --accent-green: #00e676;
      --text-main: #f8fafc;
      --text-muted: #94a3b8;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
      font-family: 'Outfit', -apple-system, sans-serif;
      background: var(--bg-dark);
      background-image: 
        radial-gradient(at 0% 0%, rgba(0, 242, 254, 0.12) 0px, transparent 50%),
        radial-gradient(at 100% 100%, rgba(157, 78, 221, 0.12) 0px, transparent 50%);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }

    header {
      background: rgba(11, 15, 25, 0.85);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--card-border);
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .brand-logo {
      width: 36px;
      height: 36px;
      background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 1.2rem;
      color: #000;
      box-shadow: 0 0 15px rgba(0, 242, 254, 0.4);
    }

    .brand h1 {
      font-size: 1.3rem;
      font-weight: 700;
      letter-spacing: 0.5px;
      background: linear-gradient(90deg, #fff, var(--text-muted));
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .status-badges {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .badge {
      font-size: 0.75rem;
      font-family: 'JetBrains Mono', monospace;
      padding: 6px 12px;
      border-radius: 20px;
      border: 1px solid var(--card-border);
      background: rgba(255, 255, 255, 0.05);
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--accent-green);
      box-shadow: 0 0 8px var(--accent-green);
    }
    .dot.spi { background: var(--accent-cyan); box-shadow: 0 0 8px var(--accent-cyan); }

    main {
      flex: 1;
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
      width: 100%;
      display: grid;
      grid-template-columns: 1fr 380px;
      gap: 24px;
    }

    @media (max-width: 960px) {
      main { grid-template-columns: 1fr; }
    }

    .card {
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 20px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
      display: flex;
      flex-direction: column;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .card-title {
      font-size: 1.1rem;
      font-weight: 600;
      color: #fff;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .map-container {
      position: relative;
      width: 100%;
      aspect-ratio: 1 / 1;
      max-height: 650px;
      background: rgba(5, 8, 15, 0.9);
      border-radius: 12px;
      border: 1px solid rgba(0, 242, 254, 0.2);
      overflow: hidden;
      cursor: crosshair;
    }

    canvas {
      width: 100%;
      height: 100%;
      display: block;
    }

    .map-hint {
      position: absolute;
      bottom: 12px;
      left: 12px;
      background: rgba(0,0,0,0.6);
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 0.75rem;
      color: var(--accent-cyan);
      backdrop-filter: blur(4px);
    }

    .controls-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-top: 16px;
    }

    .btn {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--card-border);
      color: #fff;
      padding: 14px;
      border-radius: 10px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.9rem;
    }

    .btn:hover {
      background: rgba(0, 242, 254, 0.15);
      border-color: var(--accent-cyan);
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 242, 254, 0.2);
    }

    .btn:active { transform: translateY(0); }

    .btn.stop {
      background: rgba(255, 0, 127, 0.2);
      border-color: var(--accent-pink);
      color: #ff4da6;
      grid-column: span 3;
    }
    .btn.stop:hover {
      background: rgba(255, 0, 127, 0.4);
      box-shadow: 0 4px 16px rgba(255, 0, 127, 0.4);
    }

    .object-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-height: 280px;
      overflow-y: auto;
      margin-top: 12px;
    }

    .object-item {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--card-border);
      padding: 10px 14px;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      cursor: pointer;
      transition: background 0.2s;
    }

    .object-item:hover {
      background: rgba(0, 242, 254, 0.1);
      border-color: var(--accent-cyan);
    }

    .obj-info {
      display: flex;
      flex-direction: column;
    }
    .obj-name { font-weight: 600; font-size: 0.9rem; }
    .obj-coords { font-size: 0.75rem; color: var(--text-muted); font-family: monospace; }
    .obj-btn {
      font-size: 0.7rem;
      padding: 4px 8px;
      border-radius: 4px;
      background: var(--accent-purple);
      color: white;
      border: none;
      cursor: pointer;
    }

    .telemetry-table {
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.8rem;
      color: var(--text-muted);
      width: 100%;
      border-collapse: collapse;
      margin-top: 12px;
    }
    .telemetry-table td { padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
    .telemetry-table td:last-child { text-align: right; color: var(--accent-cyan); }
  </style>
</head>
<body>

  <header>
    <div class="brand">
      <div class="brand-logo">H</div>
      <h1>HOUND ROVER AP</h1>
    </div>
    <div class="status-badges">
      <div class="badge"><div class="dot"></div> AP: 192.168.4.1</div>
      <div class="badge"><div class="dot spi"></div> SPI: Arduino Uno ACK</div>
      <div class="badge" id="modeBadge">Mode: READY</div>
    </div>
  </header>

  <main>
    <div class="card">
      <div class="card-header">
        <div class="card-title">🌐 2D Spatial Vision Map</div>
        <button class="btn" style="padding: 6px 12px; font-size:0.75rem;" onclick="resetMap()">Reset Map</button>
      </div>
      <div class="map-container" id="mapWrapper">
        <canvas id="mapCanvas"></canvas>
        <div class="map-hint">💡 Click anywhere on the map or click an object to drive rover directly to it!</div>
      </div>
    </div>

    <div style="display:flex; flex-direction:column; gap:24px;">
      <div class="card">
        <div class="card-title">🕹️ Manual Rover Controls</div>
        <div class="controls-grid">
          <div></div>
          <button class="btn" onclick="sendMotion('DRIVE_FORWARD')">▲ FWD</button>
          <div></div>
          <button class="btn" onclick="sendMotion('ROTATE_LEFT')">◄ LEFT</button>
          <button class="btn" onclick="sendMotion('DRIVE_FORWARD')">▲</button>
          <button class="btn" onclick="sendMotion('ROTATE_RIGHT')">RIGHT ►</button>
          <button class="btn stop" onclick="sendMotion('STOP')">🛑 EMERGENCY STOP</button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">🎯 Vision Detected Objects (2D)</div>
        <div class="object-list" id="objectList">
          <div style="color:var(--text-muted); font-size:0.8rem; text-align:center; padding:12px;">No objects detected yet</div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">📊 SPI & Rover Telemetry</div>
        <table class="telemetry-table">
          <tr><td>Rover Coordinates</td><td id="telPos">X: 0.00m, Y: 0.00m</td></tr>
          <tr><td>Rover Heading</td><td id="telHeading">0.0°</td></tr>
          <tr><td>SPI Bus Target</td><td id="telSpi">Arduino Uno (Mode 0)</td></tr>
          <tr><td>Last Action</td><td id="telAction">IDLE</td></tr>
        </table>
      </div>
    </div>
  </main>

  <script>
    const canvas = document.getElementById('mapCanvas');
    const ctx = canvas.getContext('2d');
    const wrapper = document.getElementById('mapWrapper');

    let roverPos = { x: 0, y: 0, heading: 0 };
    let objects = [];
    let clickTarget = null;
    const viewScale = 60; // 60 pixels = 1 meter

    function resizeCanvas() {
      canvas.width = wrapper.clientWidth;
      canvas.height = wrapper.clientHeight;
      drawMap();
    }
    window.addEventListener('resize', resizeCanvas);

    function worldToScreen(wx, wy) {
      const cx = canvas.width / 2;
      const cy = canvas.height / 2;
      return {
        x: cx + wx * viewScale,
        y: cy - wy * viewScale
      };
    }

    function screenToWorld(sx, sy) {
      const cx = canvas.width / 2;
      const cy = canvas.height / 2;
      return {
        x: (sx - cx) / viewScale,
        y: (cy - sy) / viewScale
      };
    }

    function drawMap() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const cx = canvas.width / 2;
      const cy = canvas.height / 2;

      // Draw Grid Lines
      ctx.strokeStyle = 'rgba(0, 242, 254, 0.1)';
      ctx.lineWidth = 1;
      for (let x = -10; x <= 10; x++) {
        const pt = worldToScreen(x, 0);
        ctx.beginPath(); ctx.moveTo(pt.x, 0); ctx.lineTo(pt.x, canvas.height); ctx.stroke();
      }
      for (let y = -10; y <= 10; y++) {
        const pt = worldToScreen(0, y);
        ctx.beginPath(); ctx.moveTo(0, pt.y); ctx.lineTo(canvas.width, pt.y); ctx.stroke();
      }

      // Draw Origin Crosshair
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
      ctx.beginPath(); ctx.moveTo(cx - 15, cy); ctx.lineTo(cx + 15, cy); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(cx, cy - 15); ctx.lineTo(cx, cy + 15); ctx.stroke();

      // Draw Radar FOV Cone
      const rScreen = worldToScreen(roverPos.x, roverPos.y);
      ctx.fillStyle = 'rgba(0, 242, 254, 0.08)';
      ctx.beginPath();
      ctx.moveTo(rScreen.x, rScreen.y);
      const angle = (roverPos.heading - 90) * Math.PI / 180;
      ctx.arc(rScreen.x, rScreen.y, 220, angle - Math.PI/4, angle + Math.PI/4);
      ctx.closePath();
      ctx.fill();

      // Draw Objects
      objects.forEach(obj => {
        const spt = worldToScreen(obj.x, obj.y);
        ctx.fillStyle = '#ff007f';
        ctx.beginPath();
        ctx.arc(spt.x, spt.y, 8, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.stroke();

        ctx.fillStyle = '#fff';
        ctx.font = '12px Outfit';
        ctx.fillText(obj.label || obj.id, spt.x + 12, spt.y + 4);
      });

      // Draw Click Target Marker
      if (clickTarget) {
        const tpt = worldToScreen(clickTarget.x, clickTarget.y);
        ctx.strokeStyle = '#00f2fe';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(tpt.x, tpt.y, 14, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(tpt.x, tpt.y, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#00f2fe';
        ctx.fill();
      }

      // Draw Rover Icon
      ctx.save();
      ctx.translate(rScreen.x, rScreen.y);
      ctx.rotate((roverPos.heading) * Math.PI / 180);

      // Rover Body Triangle
      ctx.fillStyle = '#00e676';
      ctx.beginPath();
      ctx.moveTo(0, -14);
      ctx.lineTo(10, 10);
      ctx.lineTo(-10, 10);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    }

    canvas.addEventListener('click', (e) => {
      const rect = canvas.getBoundingClientRect();
      const sx = e.clientX - rect.left;
      const sy = e.clientY - rect.top;
      const worldPt = screenToWorld(sx, sy);

      // Check if clicked near an object
      const clickedObj = objects.find(o => Math.hypot(o.x - worldPt.x, o.y - worldPt.y) < 0.4);
      if (clickedObj) {
        moveToLocation(clickedObj.x, clickedObj.y, clickedObj.label);
      } else {
        moveToLocation(worldPt.x, worldPt.y, 'Target Position');
      }
    });

    async function moveToLocation(x, y, label = 'Location') {
      clickTarget = { x, y };
      drawMap();
      document.getElementById('telAction').innerText = `Moving to (${x.toFixed(2)}, ${y.toFixed(2)})`;

      try {
        const res = await fetch('/api/control/location', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            protocolVersion: 1,
            type: 'location_command',
            id: crypto.randomUUID(),
            sentAtMs: Date.now(),
            targetX: x,
            targetY: y,
            speed: 1.0,
            reason: `user_click_${label}`
          })
        });
        const data = await res.json();
        console.log('Location Command sent:', data);
      } catch (err) {
        console.error('Failed to send location command:', err);
      }
    }

    async function sendMotion(intent) {
      document.getElementById('telAction').innerText = `Motion: ${intent}`;
      try {
        await fetch('/api/control/motion', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ intent, durationMs: 250 })
        });
      } catch (err) {
        console.error(err);
      }
    }

    async function fetchMapState() {
      try {
        const res = await fetch('/api/map');
        const data = await res.json();
        roverPos = { x: data.roverX, y: data.roverY, heading: data.roverHeading };
        objects = data.objects || [];
        updateObjectList();
        drawMap();

        document.getElementById('telPos').innerText = `X: ${roverPos.x.toFixed(2)}m, Y: ${roverPos.y.toFixed(2)}m`;
        document.getElementById('telHeading').innerText = `${roverPos.heading.toFixed(1)}°`;
      } catch (err) {
        console.error(err);
      }
    }

    function updateObjectList() {
      const listEl = document.getElementById('objectList');
      if (!objects || objects.length === 0) {
        listEl.innerHTML = '<div style="color:var(--text-muted); font-size:0.8rem; text-align:center; padding:12px;">No objects detected yet</div>';
        return;
      }
      listEl.innerHTML = objects.map(o => `
        <div class="object-item" onclick="moveToLocation(${o.x}, ${o.y}, '${o.label}')">
          <div class="obj-info">
            <span class="obj-name">${o.label}</span>
            <span class="obj-coords">X: ${o.x.toFixed(2)}m, Y: ${o.y.toFixed(2)}m (${(o.confidence * 100).toFixed(0)}%)</span>
          </div>
          <button class="obj-btn">MOVE TO</button>
        </div>
      `).join('');
    }

    async function resetMap() {
      await fetch('/api/map/reset', { method: 'POST' });
      clickTarget = null;
      fetchMapState();
    }

    setInterval(fetchMapState, 1000);
    window.onload = resizeCanvas;
  </script>
</body>
</html>
"""
