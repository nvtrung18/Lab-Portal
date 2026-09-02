from __future__ import annotations

import hashlib
import os
from pathlib import Path
from urllib.request import urlopen


MODELS = {
    "face_detection_yunet_2023mar.onnx": (
        "https://huggingface.co/opencv/face_detection_yunet/resolve/3cc26e7f1014a5ee5d74a42acee58bafc9d0a310/face_detection_yunet_2023mar.onnx",
        "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4",
    ),
    "face_recognition_sface_2021dec.onnx": (
        "https://huggingface.co/opencv/face_recognition_sface/resolve/3d7082438a6e4551e840c9b2bb60b71e8da4b524/face_recognition_sface_2021dec.onnx",
        "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79",
    ),
    "minifasnet_v2.onnx": (
        "https://huggingface.co/garciafido/minifasnet-v2-anti-spoofing-onnx/resolve/d29c87568ca9b5662da803b10f217c4db20b142b/minifasnet_v2.onnx",
        "d7b3cd9ba8a7ceb13baa8c4720902e27ca3112eff52f926c08804af6b6eecc7b",
    ),
}


def digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def main() -> None:
    target = Path(os.getenv("FACE_MODEL_DIR", "/app/models"))
    target.mkdir(parents=True, exist_ok=True)
    for filename, (url, expected) in MODELS.items():
        destination = target / filename
        if destination.is_file() and digest(destination) == expected:
            continue
        temporary = destination.with_suffix(destination.suffix + ".part")
        with urlopen(url, timeout=180) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        actual = digest(temporary)
        if actual != expected:
            temporary.unlink(missing_ok=True)
            raise RuntimeError(f"Checksum mismatch for {filename}: {actual}")
        temporary.replace(destination)


if __name__ == "__main__":
    main()
