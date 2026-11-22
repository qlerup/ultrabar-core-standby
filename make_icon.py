"""Draw the original PC Standby Store icon (Pillow required)."""
from pathlib import Path
from PIL import Image, ImageDraw

image = Image.new("RGB", (1024, 1024), "#161B2D")
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((168, 248, 856, 680), radius=56, outline="#B8ADFF", width=40)
draw.line((512, 680, 512, 784), fill="#B8ADFF", width=40)
draw.rounded_rectangle((384, 764, 640, 804), radius=20, fill="#B8ADFF")
points = [(x * 4, y * 4) for x, y in [(72, 116), (101, 116), (118, 94),
                                       (139, 140), (156, 116), (182, 116)]]
draw.line(points, fill="#7DF2CB", width=38, joint="curve")
for x, y in points:
    draw.ellipse((x - 19, y - 19, x + 19, y + 19), fill="#7DF2CB")
image.resize((256, 256), Image.Resampling.LANCZOS).save(Path(__file__).with_name("icon.png"))
