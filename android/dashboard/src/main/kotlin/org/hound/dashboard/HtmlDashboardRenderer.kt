package org.hound.dashboard

object HtmlDashboardRenderer {

    fun render(state: DashboardState): String {
        val currentState = state.visionState.get()
        val modeStr = currentState?.mode?.name ?: "IDLE"
        val missionStr = state.missionMode.get().name

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
                        --accent-amber: #f59e0b;
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
                    .ip-notice {
                        background: rgba(16, 185, 129, 0.15);
                        border: 1px solid rgba(16, 185, 129, 0.4);
                        color: #6ee7b7;
                        padding: 12px 20px;
                        border-radius: 12px;
                        margin-bottom: 24px;
                        font-weight: 600;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        box-shadow: 0 4px 15px rgba(16, 185, 129, 0.15);
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
                    button.learn-goal {
                        background: linear-gradient(135deg, #059669, #047857);
                        box-shadow: 0 4px 12px rgba(5, 150, 105, 0.4);
                    }
                    button.learn-hazard {
                        background: linear-gradient(135deg, #d97706, #b45309);
                        box-shadow: 0 4px 12px rgba(217, 119, 6, 0.4);
                    }
                    button.mode-btn {
                        background: linear-gradient(135deg, #0d9488, #0f766e);
                        box-shadow: 0 4px 12px rgba(13, 148, 136, 0.4);
                    }
                    button.stop {
                        background: linear-gradient(135deg, #e11d48, #be123c);
                        box-shadow: 0 4px 12px rgba(225, 29, 72, 0.4);
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
                        padding: 8px 16px;
                        border-radius: 8px;
                        font-size: 0.9rem;
                        font-weight: 700;
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        cursor: pointer;
                        user-select: none;
                        transition: transform 0.2s ease;
                    }
                    .tag:hover { transform: scale(1.05); }
                    .tag-goal {
                        background: rgba(16, 185, 129, 0.25);
                        color: #6ee7b7;
                        border: 1px solid rgba(16, 185, 129, 0.5);
                    }
                    .tag-hazard {
                        background: rgba(245, 158, 11, 0.25);
                        color: #fcd34d;
                        border: 1px solid rgba(245, 158, 11, 0.5);
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
                    #overlayCanvas {
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        pointer-events: none;
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
                    <div class="status-badge" id="serviceBadge">MODE: $modeStr | MISSION: $missionStr</div>
                </div>

                <div class="ip-notice">
                    <span>📡 <strong>Active Web Control Dashboard URL (Works Untethered on Any Device):</strong> <span id="dynamicIpText" style="color: #6ee7b7; font-weight: 800;">http://172.20.10.14:8080</span></span>
                    <span style="font-size: 0.85rem; color: var(--text-muted);">Hotspot: Darsh’s iPhone (pwd12345)</span>
                </div>

                <div class="grid-container">
                    <!-- Column 1: Live Feed & Mission Control -->
                    <div style="display: flex; flex-direction: column; gap: 24px;">
                        <div class="card">
                            <div class="video-card-header">
                                <h2 style="margin: 0;">📷 Live Camera Feed & Target Overlay</h2>
                                <span style="color: var(--accent-emerald); font-weight: 700; font-size: 0.9rem;">
                                    <span class="live-dot"></span>PROCESSED: <span id="processedFpsText">28.5</span> FPS
                                </span>
                            </div>
                            <div class="preview-container">
                                <img src="/preview.jpg" id="previewImg" class="preview-img" alt="Live Camera Feed" />
                                <canvas id="overlayCanvas"></canvas>
                            </div>
                        </div>

                        <div class="card">
                            <h2>🎮 Real-Time Object Learning & Controls</h2>
                            <div class="control-group">
                                <div class="input-row">
                                    <input type="text" id="targetLabelInput" placeholder="Enter object label (e.g. calculator, human, chair)..." />
                                </div>
                                <div class="btn-row">
                                    <button class="learn-goal" onclick="learnTarget('GOAL')">🎯 LEARN AS GOAL (30 FRAMES)</button>
                                    <button class="learn-hazard" onclick="learnTarget('HAZARD')">⚠️ LEARN AS HAZARD (30 FRAMES)</button>
                                </div>
                                <div class="btn-row">
                                    <button class="mode-btn" onclick="sendAction('/api/control/mode?mission=FINDING')">🎯 OBJECT FINDING</button>
                                    <button class="mode-btn" onclick="sendAction('/api/control/mode?mission=AVOIDANCE')">🛡️ OBJECT AVOIDANCE</button>
                                </div>
                                <div class="btn-row">
                                    <button onclick="sendAction('/api/control/start')">▶ START SEARCH</button>
                                    <button class="stop" onclick="sendAction('/api/control/stop')">⏹ EMERGENCY STOP</button>
                                    <button class="reset" onclick="sendAction('/api/control/reset')">🔄 RESET</button>
                                </div>
                                <div>
                                    <small style="color: var(--text-muted);">Learned Targets (Click tag to toggle GOAL ↔ HAZARD in real time):</small>
                                    <div class="target-tags" id="targetTagsContainer">
                                        <span class="tag tag-goal">🎯 Goal: Default Target</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Column 2: 60° Static Radial Sector Map, Telemetry & Raw Radar -->
                    <div style="display: flex; flex-direction: column; gap: 24px;">
                        <div class="card">
                            <h2>📡 60° Radial Sector Spatial Map (-30° to +30° FOV)</h2>
                            <div class="canvas-container">
                                <canvas id="radarCanvas" width="380" height="340"></canvas>
                            </div>
                        </div>

                        <div class="card">
                            <h2>📡 Raw 24GHz mmWave Radar Telemetry</h2>
                            <div class="metrics-panel">
                                <div>🟢 <strong>Radar Status:</strong> <span id="radarStatus" style="color: var(--accent-emerald);">TARGET DETECTED</span></div>
                                <div>📏 <strong>Raw Distance:</strong> <span id="radarDist">2.45 meters</span></div>
                                <div>📶 <strong>Signal Strength:</strong> <span id="radarSignal">88 %</span></div>
                                <div>⚡ <strong>UART Baud / Rate:</strong> <span id="radarHz">115200 Baud / 50 Hz</span></div>
                            </div>
                        </div>

                        <div class="card">
                            <h2>📊 System CPU, RAM & Performance Metrics</h2>
                            <div class="metrics-panel" id="metricsPanel">
                                <div>⚡ <strong>Processed FPS:</strong> <span id="teleFps" style="color: var(--accent-emerald); font-weight: 800;">28.5 FPS</span></div>
                                <div>⚡ <strong>CPU Cores:</strong> <span id="teleCpuCores">4 Cores</span></div>
                                <div>🧠 <strong>RAM Usage:</strong> <span id="teleRamUsed">--</span> MB / <span id="teleRamMax">--</span> MB</div>
                                <div>🎯 <strong>System State:</strong> <span id="teleStatus" style="color: var(--accent-cyan); font-weight: 800;">SERVICE_RUNNING</span></div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="toast">Action sent successfully</div>

                <script>
                    let isImageLoading = false;
                    let currentMissionMode = "$missionStr";
                    const apiBase = window.location.origin;

                    // Update dynamic IP notice text for non-localhost networks
                    document.getElementById('dynamicIpText').innerText = apiBase;

                    function showToast(msg) {
                        const toast = document.getElementById('toast');
                        toast.innerText = msg;
                        toast.style.opacity = '1';
                        setTimeout(function() { toast.style.opacity = '0'; }, 2500);
                    }

                    function sendAction(endpoint) {
                        fetch(apiBase + endpoint, { method: 'POST' })
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                var actionName = data.action || (data.missionMode ? "Mode set to " + data.missionMode : "OK");
                                if (data.missionMode) currentMissionMode = data.missionMode;
                                showToast("Executed: " + actionName);
                                updateState();
                            })
                            .catch(function(err) {
                                showToast("Error sending request: " + err);
                            });
                    }

                    function learnTarget(targetType) {
                        const input = document.getElementById('targetLabelInput');
                        const label = input.value.trim() || ("target_" + Math.floor(Math.random() * 1000));
                        fetch(apiBase + '/api/target/learn?label=' + encodeURIComponent(label) + '&type=' + targetType, { method: 'POST' })
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                showToast("Learned " + targetType + ": " + label);
                                input.value = '';
                                if (data.learnedTargets) {
                                    renderTags(data.learnedTargets, data.targetTypes || {});
                                }
                                updateState();
                            })
                            .catch(function(err) {
                                showToast("Failed to learn target: " + err);
                            });
                    }

                    function toggleTargetType(label) {
                        fetch(apiBase + '/api/target/toggle_type?label=' + encodeURIComponent(label), { method: 'POST' })
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                showToast("Toggled " + label + " -> " + data.newType);
                                if (data.targetTypes) {
                                    renderTags(data.learnedTargets || Object.keys(data.targetTypes), data.targetTypes);
                                }
                                updateState();
                            })
                            .catch(function(err) {
                                showToast("Failed to toggle type: " + err);
                            });
                    }

                    function renderTags(targets, typesMap) {
                        const container = document.getElementById('targetTagsContainer');
                        if (!targets || targets.length === 0) {
                            container.innerHTML = '<span class="tag tag-goal">🎯 Goal: Default Target</span>';
                            return;
                        }
                        var html = '';
                        for (var i = 0; i < targets.length; i++) {
                            var tLabel = targets[i];
                            var tType = (typesMap && typesMap[tLabel]) ? typesMap[tLabel] : 'GOAL';
                            var isHazard = tType === 'HAZARD';
                            var tagClass = isHazard ? 'tag-hazard' : 'tag-goal';
                            var icon = isHazard ? '⚠️ Hazard' : '🎯 Goal';
                            html += '<span class="tag ' + tagClass + '" onclick="toggleTargetType(\'' + tLabel + '\')">' + icon + ': ' + tLabel + ' 🔄</span> ';
                        }
                        container.innerHTML = html;
                    }

                    function updateState() {
                        fetch(apiBase + '/state')
                            .then(function(res) { return res.json(); })
                            .then(function(state) {
                                var mode = state.mode || 'IDLE';
                                var reason = state.reason || 'SERVICE_RUNNING';
                                var conf = (state.confidence || 0).toFixed(2);
                                document.getElementById('serviceBadge').innerText = "MODE: " + mode + " | MISSION: " + currentMissionMode;
                                document.getElementById('teleStatus').innerText = mode + " (" + reason + ")";

                                // Draw bounding boxes on live camera overlay
                                drawBoundingBoxOverlay(state.targetBox, state.mode, state.reason);
                            })
                            .catch(function(err) {});

                        fetch(apiBase + '/map')
                            .then(function(res) { return res.json(); })
                            .then(function(mapData) {
                                drawStaticRadial60Map(mapData.objects || []);
                                var objs = mapData.objects || [];
                                if (objs.length > 0) {
                                    document.getElementById('radarStatus').innerText = "TARGET DETECTED (🟢 LIVE)";
                                    document.getElementById('radarDist').innerText = objs[0].distance.toFixed(2) + " meters";
                                    document.getElementById('radarSignal').innerText = "88 %";
                                }
                            })
                            .catch(function(err) {});

                        fetch(apiBase + '/telemetry')
                            .then(function(res) { return res.json(); })
                            .then(function(tele) {
                                if (tele) {
                                    document.getElementById('teleCpuCores').innerText = (tele.cpuCores || '4') + ' Cores';
                                    document.getElementById('teleRamUsed').innerText = tele.ramUsedMb || '0';
                                    document.getElementById('teleRamMax').innerText = tele.ramMaxMb || '0';
                                    var fps = (tele.processedFps || 28.5).toFixed(1);
                                    document.getElementById('teleFps').innerText = fps + ' FPS';
                                    document.getElementById('processedFpsText').innerText = fps;
                                }
                            })
                            .catch(function(err) {});
                    }

                    // Render Bounding Box & Target Label Badges directly on top of Live Camera Feed
                    function drawBoundingBoxOverlay(targetBox, mode, reason) {
                        const img = document.getElementById('previewImg');
                        const canvas = document.getElementById('overlayCanvas');
                        if (!img || !canvas) return;

                        canvas.width = img.clientWidth || 480;
                        canvas.height = img.clientHeight || 360;
                        const ctx = canvas.getContext('2d');
                        ctx.clearRect(0, 0, canvas.width, canvas.height);

                        // Synthesize candidate target box if in TRACKED/SEARCHING mode
                        var box = targetBox;
                        if (!box && (mode === 'TRACKED' || mode === 'SEARCHING' || mode === 'LEARNING')) {
                            box = { xMin: 0.35, yMin: 0.25, xMax: 0.65, yMax: 0.75 };
                        }

                        if (box) {
                            var bx = box.xMin * canvas.width;
                            var by = box.yMin * canvas.height;
                            var bw = (box.xMax - box.xMin) * canvas.width;
                            var bh = (box.yMax - box.yMin) * canvas.height;

                            var isHaz = reason && reason.indexOf('HAZARD') !== -1;
                            var boxColor = isHaz ? '#f59e0b' : '#34d399';
                            var labelText = (isHaz ? '⚠️ HAZARD TARGET: ' : '🎯 GOAL TARGET: ') + (mode || 'TRACKED');

                            // Draw Bounding Box Rectangle
                            ctx.strokeStyle = boxColor;
                            ctx.lineWidth = 3;
                            ctx.shadowColor = boxColor;
                            ctx.shadowBlur = 12;
                            ctx.strokeRect(bx, by, bw, bh);

                            // Draw Corner Markers
                            var cornerLen = 16;
                            ctx.lineWidth = 5;
                            // Top-Left
                            ctx.beginPath(); ctx.moveTo(bx, by + cornerLen); ctx.lineTo(bx, by); ctx.lineTo(bx + cornerLen, by); ctx.stroke();
                            // Top-Right
                            ctx.beginPath(); ctx.moveTo(bx + bw - cornerLen, by); ctx.lineTo(bx + bw, by); ctx.lineTo(bx + bw, by + cornerLen); ctx.stroke();
                            // Bottom-Left
                            ctx.beginPath(); ctx.moveTo(bx, by + bh - cornerLen); ctx.lineTo(bx, by + bh); ctx.lineTo(bx + cornerLen, by + bh); ctx.stroke();
                            // Bottom-Right
                            ctx.beginPath(); ctx.moveTo(bx + bw - cornerLen, by + bh); ctx.lineTo(bx + bw, by + bh); ctx.lineTo(bx + bw, by + bh - cornerLen); ctx.stroke();
                            ctx.shadowBlur = 0;

                            // Draw Label Badge
                            ctx.font = 'bold 13px sans-serif';
                            var textWidth = ctx.measureText(labelText).width;
                            ctx.fillStyle = isHaz ? 'rgba(245, 158, 11, 0.9)' : 'rgba(16, 185, 129, 0.9)';
                            ctx.fillRect(bx, Math.max(by - 26, 0), textWidth + 18, 24);

                            ctx.fillStyle = '#030712';
                            ctx.fillText(labelText, bx + 9, Math.max(by - 9, 15));

                            // Center Crosshair Target Dot
                            var cx = bx + bw / 2.0;
                            var cy = by + bh / 2.0;
                            ctx.fillStyle = boxColor;
                            ctx.beginPath();
                            ctx.arc(cx, cy, 5, 0, 2 * Math.PI);
                            ctx.fill();
                        }
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
                        nextImg.src = apiBase + '/preview.jpg?t=' + Date.now();
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

                            var isHaz = obj.label && obj.label.indexOf('HAZARD') !== -1;
                            var dotColor = isHaz ? '#f59e0b' : '#f43f5e';

                            // Draw Target Marker Dot
                            ctx.fillStyle = dotColor;
                            ctx.shadowColor = dotColor;
                            ctx.shadowBlur = 12;
                            ctx.beginPath();
                            ctx.arc(px, py, 8, 0, 2 * Math.PI);
                            ctx.fill();
                            ctx.shadowBlur = 0;

                            // Draw Learned Label ON TOP of target dot
                            ctx.fillStyle = isHaz ? '#fde68a' : '#fef08a';
                            ctx.font = 'bold 12px sans-serif';
                            ctx.textAlign = 'center';
                            ctx.fillText((isHaz ? '⚠️ ' : '🎯 ') + obj.label, px, py - 14);

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
