"""Build a release APK using a local Android SDK and a private signing key."""
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = Path(os.environ["ANDROID_BUILD_TOOLS"]).resolve()
PLATFORM = Path(os.environ["ANDROID_PLATFORM_JAR"]).resolve()
KEYSTORE = Path(os.environ["PC_STANDBY_KEYSTORE"]).resolve()
EXE = ".exe" if os.name == "nt" else ""


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)


with tempfile.TemporaryDirectory(prefix="pc-standby-build-") as directory:
    build = Path(directory)
    classes = build / "classes"
    classes.mkdir()
    run("javac", "-encoding", "UTF-8", "--release", "8", "-classpath", PLATFORM,
        "-d", classes, *sorted((ROOT / "src").rglob("*.java")))
    run("java", "-cp", TOOLS / "lib/d8.jar", "com.android.tools.r8.D8",
        "--lib", PLATFORM, "--min-api", "26", "--output", build,
        *sorted(classes.rglob("*.class")))
    unsigned = build / "unsigned.apk"
    run(TOOLS / f"aapt{EXE}", "package", "-f", "-M", ROOT / "AndroidManifest.xml",
        "-I", PLATFORM, "-S", ROOT / "res", "-F", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(build / "classes.dex", "classes.dex")
    aligned = build / "aligned.apk"
    run(TOOLS / f"zipalign{EXE}", "-f", "4", unsigned, aligned)
    signer = TOOLS / "lib/apksigner.jar"
    run("java", "-jar", signer, "sign", "--ks", KEYSTORE,
        "--ks-pass", "env:PC_STANDBY_KEY_PASSWORD", "--v4-signing-enabled", "false",
        "--out", ROOT / "app.apk", aligned)
    run("java", "-jar", signer, "verify", "--verbose", "--print-certs", ROOT / "app.apk")
