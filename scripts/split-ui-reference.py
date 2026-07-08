#!/usr/bin/env python3
"""Split the assistant UI reference board into individual PNG references.

Requires Pillow:
    python3 -m pip install Pillow

Crop boxes use Pillow's (left, upper, right, lower) coordinates, measured in
pixels from the top-left corner of docs/ui-reference/assistant-flow.png.
Adjust the CROP_DEFINITIONS values below when the board layout changes.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1]
UI_REFERENCE_DIR = PROJECT_ROOT / "docs" / "ui-reference"
SOURCE_IMAGE = UI_REFERENCE_DIR / "assistant-flow.png"

ASSISTANT_OUTPUT_DIR = UI_REFERENCE_DIR / "assistant"
COMPONENT_OUTPUT_DIR = UI_REFERENCE_DIR / "components"


@dataclass(frozen=True)
class CropDefinition:
    output_dir: Path
    filename: str
    box: tuple[int, int, int, int]


# Board dimensions at the time this script was created: 1536 x 1024.
# The screen crops intentionally include each phone frame and its flow label.
# Format: (left, upper, right, lower)
CROP_DEFINITIONS = [
    # Assistant flow screens.
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "01-home-empty.png",
        (242, 15, 480, 555),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "02-text-message.png",
        (489, 15, 727, 555),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "03-voice-message.png",
        (736, 15, 974, 555),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "04-room-disambiguation.png",
        (983, 15, 1232, 555),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "05-room-selected.png",
        (1248, 15, 1505, 555),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "06-task-preview.png",
        (242, 580, 480, 1000),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "07-task-created.png",
        (489, 580, 727, 1000),
    ),
    CropDefinition(
        ASSISTANT_OUTPUT_DIR,
        "08-reset-state.png",
        (736, 580, 974, 1000),
    ),

    # Reusable component references.
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "assistant-card.png",
        (18, 710, 230, 970),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "voice-bubble.png",
        (989, 627, 1216, 716),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "attachment-card.png",
        (1224, 627, 1505, 716),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "intent-badges.png",
        (989, 748, 1216, 881),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "buttons.png",
        (1224, 748, 1505, 881),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "composer.png",
        (989, 912, 1240, 990),
    ),
    CropDefinition(
        COMPONENT_OUTPUT_DIR,
        "bottom-navigation.png",
        (1254, 912, 1505, 990),
    ),
]


def validate_crop(source_size: tuple[int, int], crop: CropDefinition) -> None:
    width, height = source_size
    left, upper, right, lower = crop.box

    if left < 0 or upper < 0 or right > width or lower > height:
        raise ValueError(
            f"{crop.filename} crop {crop.box} is outside source image "
            f"bounds {(width, height)}"
        )

    if left >= right or upper >= lower:
        raise ValueError(f"{crop.filename} crop {crop.box} has invalid dimensions")


def main() -> None:
    if not SOURCE_IMAGE.exists():
        raise FileNotFoundError(f"Source image not found: {SOURCE_IMAGE}")

    ASSISTANT_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    COMPONENT_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    with Image.open(SOURCE_IMAGE) as source:
        for crop in CROP_DEFINITIONS:
            validate_crop(source.size, crop)
            output_path = crop.output_dir / crop.filename
            source.crop(crop.box).save(output_path, "PNG")
            print(f"Saved {output_path.relative_to(PROJECT_ROOT)}")


if __name__ == "__main__":
    main()
