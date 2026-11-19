"""Real segment shell branch with isolated settings and a fake native writer."""
from pathlib import Path
import os
import subprocess
import tempfile

with tempfile.TemporaryDirectory(prefix="raven-segment-mask-") as folder:
    root = Path(folder)
    tools = root / "bin"
    tools.mkdir()
    settings = root / "settings"
    settings.mkdir()
    state = root / "control"
    source = Path(__file__).resolve().parents[1] / "assets/hardware.sh"
    helper = root / "hardware.sh"
    helper.write_text(source.read_text().replace("D=/data/local/odin-controls", f"D={state}"))

    def executable(path, body):
        path.write_text("#!/bin/sh\n" + body + "\n")
        path.chmod(0o700)

    for name, body in {
        "id": "echo 0",
        "am": "echo 0",
        "stat": "echo 0",
        "getprop": 'case "$1" in ro.product.model) echo Odin3;; *) echo 35;; esac',
        "settings": f'''case "$3" in
get) if [ -f "{settings}/$5" ]; then cat "{settings}/$5";else echo null;fi;;
put) printf '%s' "$6" > "{settings}/$5";;
delete) rm -f "{settings}/$5";;
*) exit 1;; esac''',
    }.items():
        executable(tools / name, body)
    recorded = root / "writer-request"
    executable(root / "ring-segments", f'''if [ "$1" != probe ];then printf '%s|%s' "$1" "$2" > "{recorded}";fi
echo OK''')
    env = dict(os.environ, PATH=str(tools) + ":" + os.environ["PATH"])
    (settings / "joystick_light_enabled").write_text("1,1")
    zero = "0" * 24
    lit = "7F0000007F0000007F7F4000"
    for packed, requested, expected in [
        (zero + zero, "1,1", "0,0"),
        (lit + zero, "1,1", "1,0"),
        (zero + lit, "1,1", "0,1"),
        (lit + lit, "1,1", "1,1"),
        (lit + lit, "0,0", "0,0"),
    ]:
        result = subprocess.run(["sh", str(helper), "light-segments", packed, requested], env=env, capture_output=True, text=True, timeout=10)
        assert result.returncode == 0 and result.stdout.startswith("OK"), result
        assert (settings / "joystick_light_enabled").read_text() == expected
        assert recorded.read_text() == packed + "|" + expected
    assert (state / "lights/enabled").read_text().strip() == "1,1"
    print("PASS shell segment masks: all-off, either empty ring, mixed output, explicit off, original snapshot retained")
