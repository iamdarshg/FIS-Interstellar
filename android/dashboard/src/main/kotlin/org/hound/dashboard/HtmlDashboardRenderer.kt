package org.hound.dashboard

object HtmlDashboardRenderer {

    fun render(state: DashboardState): String {
        val currentState = state.visionState.get()
        val modeStr = currentState?.mode?.name ?: "IDLE"
        val timestamp = currentState?.timestampMs ?: 0L
        val battery = state.batteryPercentage.get()

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>HOUND Stationary Dashboard</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121212; color: #e0e0e0; margin: 0; padding: 20px; }
                    .card { background: #1e1e1e; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
                    h1, h2 { color: #bb86fc; margin-top: 0; }
                    .status { font-size: 1.2rem; font-weight: bold; color: #03dac6; }
                    .btn-group { display: flex; gap: 10px; margin-top: 15px; }
                    button { background: #3700b3; color: white; border: none; padding: 10px 20px; border-radius: 4px; font-weight: bold; cursor: pointer; }
                    button:hover { background: #6200ee; }
                    button.stop { background: #cf6679; }
                    button.stop:hover { background: #b00020; }
                    img { max-width: 100%; height: auto; border-radius: 4px; background: #000; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>HOUND Stationary Service</h1>
                    <p class="status">Mode: $modeStr | Timestamp: $timestamp | Battery: $battery%</p>
                    <div class="btn-group">
                        <form action="/api/target/learn" method="post"><button type="submit">LEARN</button></form>
                        <form action="/api/control/start" method="post"><button type="submit">START</button></form>
                        <form action="/api/control/stop" method="post"><button type="submit" class="stop">STOP</button></form>
                        <form action="/api/control/reset" method="post"><button type="submit">RESET</button></form>
                    </div>
                </div>
                <div class="card">
                    <h2>Live Camera Preview</h2>
                    <img src="/preview.jpg" alt="Preview Frame" />
                </div>
                <div class="card">
                    <h2>State Metrics</h2>
                    <pre>${state.lastMetrics.get()}</pre>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
