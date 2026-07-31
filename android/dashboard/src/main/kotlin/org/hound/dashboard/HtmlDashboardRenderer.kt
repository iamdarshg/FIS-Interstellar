package org.hound.dashboard

object HtmlDashboardRenderer {

    fun render(state: DashboardState): String {
        val currentState = state.visionState.get()
        val modeStr = currentState?.mode?.name ?: "IDLE"
        val battery = state.batteryPercentage.get()

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>HOUND High-FPS Vision & 60° Spatial Radar</title>
                <style>
                    :root {
                        --bg-dark: #030712;
                        --card-bg: rgba(17, 24, 39, 0.75);
                        --card-border: rgba(168, 85, 247, 0.25);
                        --accent-purple: #c084fc;
                        --accent-cyan: #22d3ee;
                        --accent-emerald: #34d399;
                        --accent-rose: #fb7185;
                        --text-light: #f9fafb;
                        --text-muted: #9ca3af;
                    }
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: radial-gradient(circle at 50% 0%, #1e1b4b 0%, #030712 70%);
                        color: var(--text-light);
                        margin: 0;
                        padding: 24px;
                        min-height: 100vh;
                    }
                    .dashboard-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 1px solid rgba(168, 85, 247, 0.2);
                        padding-bottom: 20px;
                        margin-bottom: 24px;
                        backdrop-filter: blur(12px);
                    }
                    .header-title {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .header-title h1 {
                        margin: 0;
                        font-size: 2rem;
                        background: linear-gradient(135deg, #e9d5ff 0%, #a855f7 50%, #06b6d4 100%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                        font-weight: 800;
                        letter-spacing: -0.02em;
                    }
                    .status-badge {
                        background: rgba(30, 41, 59, 0.8);
                        padding: 8px 20px;
                        border-radius: 9999px;
                        font-weight: 700;
                        font-size: 0.95rem;
                        color: var(--accent-cyan);
                        border: 1px solid rgba(34, 211, 238, 0.4);
                        box-shadow: 0 0 15px rgba(34, 211, 238, 0.2);
                    }
                    .grid-container {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 24px;
                    }
                    @media (max-width: 1024px) {
                        .grid-container { grid-template-columns: 1fr; }
                    }
                    .card {
                        background: var(--card-bg);
                        border: 1px solid var(--card-border);
                        border-radius: 16px;
                        padding: 24px;
                        backdrop-filter: blur(16px);
                        box-shadow: 0 20px 30px -10px rgba(0, 0, 0, 0.7);
                        transition: border-color 0.3s ease;
                    }
                    .card:hover {
                        border-color: rgba(168, 85, 247, 0.5);
                    }
                    .card h2 {
                        margin-top: 0;
                        font-size: 1.25rem;
                        color: var(--accent-purple);
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        margin-bottom: 18px;
                    }
                    .control-group {
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                    }
                    .input-row {
                        display: flex;
                        gap: 12px;
                    }
                    input[type="text"] {
                        flex: 1;
                        background: rgba(3, 7, 18, 0.8);
                        border: 1px solid rgba(168, 85, 247, 0.3);
                        color: var(--text-light);
                        padding: 12px 18px;
                        border-radius: 10px;
                        font-size: 0.95rem;
                        transition: all 0.2s ease;
                    }
                    input[type="text"]:focus {
                        outline: none;
                        border-color: var(--accent-cyan);
                        box-shadow: 0 0 12px rgba(34, 211, 238, 0.3);
                    }
                    .btn-row {
                        display: flex;
                        gap: 12px;
                        flex-wrap: wrap;
                    }
                    button {
                        background: linear-gradient(135deg, #7e22ce, #6b21a8);
                        color: white;
                        border: none;
                        padding: 12px 22px;
                        border-radius: 10px;
                        font-weight: 700;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        font-size: 0.95rem;
                        box-shadow: 0 4px 12px rgba(126, 34, 206, 0.4);
                    }
                    button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 6px 16px rgba(126, 34, 206, 0.6);
                    }
                    button.learn {
                        background: linear-gradient(135deg, #0284c7, #0369a1);
                        box-shadow: 0 4px 12px rgba(2, 132, 199, 0.4);
                    }
                    button.learn:hover {
                        box-shadow: 0 6px 16px rgba(2, 132, 199, 0.6);
                    }
                    button.stop {
                        background: linear-gradient(135deg, #e11d48, #be123c);
                        box-shadow: 0 4px 12px rgba(225, 29, 72, 0.4);
                    }
                    button.stop:hover {
                        box-shadow: 0 6px 16px rgba(225, 29, 72, 0.6);
                    }
                    button.reset {
                        background: linear-gradient(135deg, #4b5563, #374151);
                        box-shadow: 0 4px 12px rgba(75, 85, 99, 0.4);
                    }
                    .target-tags {
                        display: flex;
                        gap: 10px;
                        flex-wrap: wrap;
                        margin-top: 10px;
                    }
                    .tag {
                        background: rgba(168, 85, 247, 0.2);
                        color: #e9d5ff;
                        border: 1px solid rgba(168, 85, 247, 0.4);
                        padding: 6px 14px;
                        border-radius: 8px;
                        font-size: 0.9rem;
                        font-weight: 600;
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                    }
                    .video-card-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 14px;
                    }
                    .live-dot {
                        display: inline-block;
                        width: 10px;
                        height: 10px;
                        background: #10b981;
                        border-radius: 50%;
                        margin-right: 6px;
                        box-shadow: 0 0 10px #10b981;
                        animation: pulse 1.5s infinite;
                    }
                    @keyframes pulse {
                        0% { opacity: 1; }
                        50% { opacity: 0.4; }
                        100% { opacity: 1; }
                    }
                    .preview-container {
                        position: relative;
                        width: 100%;
                        background: #000;
                        border-radius: 12px;
                        overflow: hidden;
                        border: 1px solid rgba(168, 85, 247, 0.3);
                        box-shadow: 0 0 20px rgba(0, 0, 0, 0.8);
                    }
                    img.preview-img {
                        width: 100%;
                        max-height: 360px;
                        object-fit: contain;
                        display: block;
                    }
                    .canvas-container {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        background: rgba(3, 7, 18, 0.9);
                        border-radius: 12px;
                        padding: 16px;
                        border: 1px solid rgba(168, 85, 247, 0.3);
                    }
                    canvas { border-radius: 8px; background: #020617; }
                    .metrics-panel {
                        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                        background: rgba(3, 7, 18, 0.9);
                        padding: 16px;
                        border-radius: 10px;
                        color: var(--accent-cyan);
                        font-size: 0.9rem;
                        line-height: 1.8;
                        border: 1px solid rgba(34, 211, 238, 0.2);
                    }
                    #toast {
                        position: fixed;
                        bottom: 24px;
                        right: 24px;
                        background: #10b981;
                        color: white;
                        padding: 14px 24px;
                        border-radius: 12px;
                        font-weight: 700;
                        box-shadow: 0 10px 25px rgba(0,0,0,0.6);
                        opacity: 0;
                        transition: opacity 0.3s ease;
                        pointer-events: none;
                        z-index: 9999;
                    }
                </style>
            </head>
            <body>
                <div class="dashboard-header">
                    <div class="header-title">
                        <h1>HOUND High-FPS Vision & 60° Spatial Radar</h1>
                    </div>
                    <div class="status-badge" id="serviceBadge">MODE: $modeStr | Battery: $battery%</div>
                </div>

                <div class="grid-container">
                    <!-- Column 1: Live Feed (Default Display) & Mission Control -->
                    <div style="display: flex; flex-direction: column; gap: 24px;">
                        <div class="card">
                            <div class="video-card-header">
                                <h2 style="margin: 0;">📷 Live Camera Feed</h2>
                                <span style="color: var(--accent-emerald); font-weight: 700; font-size: 0.9rem;">
                                    <span class="live-dot"></span>LIVE STREAM (>20 FPS)
                                </span>
                            </div>
                            <div class="preview-container">
                                <img src="/preview.jpg" id="previewImg" class="preview-img" alt="Live Camera Feed" />
                            </div>
                        </div>

                        <div class="card">
                            <h2>🎮 Mission Control & Object Labeling</h2>
                            <div class="control-group">
                                <div class="input-row">
                                    <input type="text" id="targetLabelInput" placeholder="Enter target label (e.g. calculator, bottle, toy)..." />
                                    <button class="learn" onclick="learnTarget()">LEARN TARGET</button>
                                </div>
                                <div class="btn-row">
                                    <button onclick="sendAction('/api/control/start')">▶ START SEARCH</button>
                                    <button class="stop" onclick="sendAction('/api/control/stop')">⏹ EMERGENCY STOP</button>
                                    <button class="reset" onclick="sendAction('/api/control/reset')">🔄 RESET</button>
                                </div>
                                <div>
                                    <small style="color: var(--text-muted);">Learned Object Labels:</small>
                                    <div class="target-tags" id="targetTagsContainer">
                                        <span class="tag">Default Target</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Column 2: 60° Static Radial Sector Map & Telemetry -->
                    <div style="display: flex; flex-direction: column; gap: 24px;">
                        <div class="card">
                            <h2>📡 60° Radial Sector Spatial Map (-30° to +30° FOV)</h2>
                            <div class="canvas-container">
                                <canvas id="radarCanvas" width="380" height="340"></canvas>
                            </div>
                        </div>

                        <div class="card">
                            <h2>📊 System CPU, RAM & Telemetry Metrics</h2>
                            <div class="metrics-panel" id="metricsPanel">
                                <div>⚡ <strong>CPU Cores:</strong> <span id="teleCpuCores">4 Cores</span></div>
                                <div>🧠 <strong>RAM Usage:</strong> <span id="teleRamUsed">--</span> MB / <span id="teleRamMax">--</span> MB</div>
                                <div>🎯 <strong>System State:</strong> <span id="teleStatus">SERVICE_RUNNING</span></div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="toast">Action sent successfully</div>

                <script>
                    let isImageLoading = false;

                    function showToast(msg) {
                        const toast = document.getElementById('toast');
                        toast.innerText = msg;
                        toast.style.opacity = '1';
                        setTimeout(function() { toast.style.opacity = '0'; }, 2500);
                    }

                    function sendAction(endpoint) {
                        fetch(endpoint, { method: 'POST' })
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                showToast("Executed: " + (data.action || "OK"));
                                updateState();
                            })
                            .catch(function(err) {
                                showToast("Error sending request: " + err);
                            });
                    }

                    function learnTarget() {
                        const input = document.getElementById('targetLabelInput');
                        const label = input.value.trim() || ("target_" + Math.floor(Math.random() * 1000));
                        fetch('/api/target/learn?label=' + encodeURIComponent(label), { method: 'POST' })
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                showToast("Learned Label: " + label);
                                input.value = '';
                                if (data.learnedTargets) {
                                    renderTags(data.learnedTargets);
                                }
                                updateState();
                            })
                            .catch(function(err) {
                                showToast("Failed to learn target: " + err);
                            });
                    }

                    function renderTags(targets) {
                        const container = document.getElementById('targetTagsContainer');
                        if (!targets || targets.length === 0) {
                            container.innerHTML = '<span class="tag">Default Target</span>';
                            return;
                        }
                        var html = '';
                        for (var i = 0; i < targets.length; i++) {
                            html += '<span class="tag">🏷️ ' + targets[i] + '</span> ';
                        }
                        container.innerHTML = html;
                    }

                    function updateState() {
                        fetch('/state')
                            .then(function(res) { return res.json(); })
                            .then(function(state) {
                                var conf = (state.confidence || 0).toFixed(2);
                                document.getElementById('serviceBadge').innerText = "MODE: " + (state.mode || 'IDLE') + " | Conf: " + conf;
                                document.getElementById('teleStatus').innerText = state.reason || "RUNNING";
                            })
                            .catch(function(err) {});

                        fetch('/map')
                            .then(function(res) { return res.json(); })
                            .then(function(mapData) {
                                drawStaticRadial60Map(mapData.objects || []);
                            })
                            .catch(function(err) {});

                        fetch('/telemetry')
                            .then(function(res) { return res.json(); })
                            .then(function(tele) {
                                if (tele) {
                                    document.getElementById('teleCpuCores').innerText = (tele.cpuCores || '4') + ' Cores';
                                    document.getElementById('teleRamUsed').innerText = tele.ramUsedMb || '0';
                                    document.getElementById('teleRamMax').innerText = tele.ramMaxMb || '0';
                                }
                            })
                            .catch(function(err) {});
                    }

                    // Smooth double-buffered camera refresh for high-FPS flicker-free streaming
                    function refreshCameraFrame() {
                        if (isImageLoading) return;
                        isImageLoading = true;
                        var nextImg = new Image();
                        nextImg.onload = function() {
                            var currentImg = document.getElementById('previewImg');
                            if (currentImg) {
                                currentImg.src = nextImg.src;
                            }
                            isImageLoading = false;
                        };
                        nextImg.onerror = function() {
                            isImageLoading = false;
                        };
                        nextImg.src = '/preview.jpg?t=' + Date.now();
                    }

                    // Render Clean Static 60-degree Radial Sector Map (-30 deg to +30 deg FOV)
                    function drawStaticRadial60Map(objects) {
                        const canvas = document.getElementById('radarCanvas');
                        const ctx = canvas.getContext('2d');
                        const cx = canvas.width / 2;
                        const cy = canvas.height - 30; // Rover origin at bottom-center
                        const radius = canvas.height - 50;

                        // Clear Canvas
                        ctx.fillStyle = '#020617';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);

                        // 60-degree FOV Angles (Straight up is -90 deg / -PI/2)
                        const startAngle = -Math.PI * 2 / 3; // -120 deg
                        const endAngle = -Math.PI / 3;        // -60 deg

                        // Draw 60-degree Sector Wedge Background
                        ctx.fillStyle = '#090d16';
                        ctx.beginPath();
                        ctx.moveTo(cx, cy);
                        ctx.arc(cx, cy, radius, startAngle, endAngle);
                        ctx.closePath();
                        ctx.fill();
                        ctx.strokeStyle = '#334155';
                        ctx.lineWidth = 2;
                        ctx.stroke();

                        // Draw Distance Concentric Arcs (0.75m, 1.5m, 2.25m, 3.0m)
                        ctx.strokeStyle = '#1e293b';
                        ctx.lineWidth = 1;
                        for (var r = 1; r <= 4; r++) {
                            const curR = (radius / 4) * r;
                            ctx.beginPath();
                            ctx.arc(cx, cy, curR, startAngle, endAngle);
                            ctx.stroke();

                            // Arc Distance Labels
                            ctx.fillStyle = '#64748b';
                            ctx.font = '10px sans-serif';
                            ctx.fillText((r * 0.75).toFixed(2) + 'm', cx + 4, cy - curR + 12);
                        }

                        // Draw Radial Angle Lines (-30°, -15°, 0°, +15°, +30°)
                        const angleSteps = [-30, -15, 0, 15, 30];
                        for (var i = 0; i < angleSteps.length; i++) {
                            const deg = angleSteps[i];
                            const rad = (deg - 90) * Math.PI / 180.0;
                            const lx = cx + Math.cos(rad) * radius;
                            const ly = cy + Math.sin(rad) * radius;

                            ctx.beginPath();
                            ctx.moveTo(cx, cy);
                            ctx.lineTo(lx, ly);
                            ctx.stroke();

                            // Angle Deg Label
                            ctx.fillStyle = '#94a3b8';
                            ctx.font = '10px sans-serif';
                            ctx.fillText(deg + '°', lx + (deg < 0 ? -22 : 4), ly + (deg === 0 ? -6 : 10));
                        }

                        // Draw Rover Origin Dot (Bottom-Center)
                        ctx.fillStyle = '#a855f7';
                        ctx.beginPath();
                        ctx.arc(cx, cy, 8, 0, 2 * Math.PI);
                        ctx.fill();

                        // Plot Spatial Objects within 60-degree FOV Sector
                        for (var j = 0; j < (objects || []).length; j++) {
                            var obj = objects[j];
                            var distScale = Math.min(obj.distance / 3.0, 1.0) * radius;
                            var objAngleRad = (obj.angle - 90) * Math.PI / 180.0;

                            var px = cx + Math.cos(objAngleRad) * distScale;
                            var py = cy + Math.sin(objAngleRad) * distScale;

                            // Draw Target Marker Dot
                            ctx.fillStyle = '#f43f5e';
                            ctx.shadowColor = '#f43f5e';
                            ctx.shadowBlur = 12;
                            ctx.beginPath();
                            ctx.arc(px, py, 8, 0, 2 * Math.PI);
                            ctx.fill();
                            ctx.shadowBlur = 0;

                            // Draw Learned Label ON TOP of target dot
                            ctx.fillStyle = '#fef08a';
                            ctx.font = 'bold 12px sans-serif';
                            ctx.textAlign = 'center';
                            ctx.fillText('🏷️ ' + obj.label, px, py - 14);

                            // Draw Target ID & Distance BELOW target dot
                            ctx.fillStyle = '#93c5fd';
                            ctx.font = '10px sans-serif';
                            ctx.fillText((obj.id || 'target') + ' (' + obj.distance.toFixed(1) + 'm)', px, py + 18);
                            ctx.textAlign = 'left';
                        }
                    }

                    // High-frequency polling timers
                    setInterval(updateState, 200);
                    setInterval(refreshCameraFrame, 150);
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
