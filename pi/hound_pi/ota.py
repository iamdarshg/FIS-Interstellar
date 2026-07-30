from __future__ import annotations

import subprocess
from pathlib import Path

REPO_URL = "https://github.com/iamdarshg/FIS-Interstellar.git"


def update_from_git(repo_dir: str | Path, branch: str = "main") -> str:
    repo_path = Path(repo_dir).resolve()
    git_dir = repo_path / ".git"
    if not git_dir.exists():
        repo_path.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "-C", str(repo_path), "init"], check=True, capture_output=True, text=True)
        subprocess.run(
            ["git", "-C", str(repo_path), "remote", "add", "origin", REPO_URL],
            check=True,
            capture_output=True,
            text=True,
        )

    fetch = subprocess.run(
        ["git", "-C", str(repo_path), "fetch", "origin", branch],
        check=True,
        capture_output=True,
        text=True,
    )
    reset = subprocess.run(
        ["git", "-C", str(repo_path), "reset", "--hard", f"origin/{branch}"],
        check=True,
        capture_output=True,
        text=True,
    )

    head = subprocess.run(
        ["git", "-C", str(repo_path), "rev-parse", "--short", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    return "\n".join(
        line
        for line in (
            fetch.stdout.strip(),
            fetch.stderr.strip(),
            reset.stdout.strip(),
            reset.stderr.strip(),
            f"HEAD={head.stdout.strip()}",
        )
        if line
    )
