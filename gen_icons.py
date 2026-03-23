import cairosvg
from PIL import Image
import io, os

base = 'D:/PROJ/MOVBILE/PROJ/breezy-weather-VN/app/src/res_fork'
svg_path = base + '/icon_source.svg'

densities = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
    'drawable':       48,
}

for folder, size in densities.items():
    out_dir = os.path.join(base, folder)
    png_bytes = cairosvg.svg2png(url=svg_path, output_width=size, output_height=size)
    img = Image.open(io.BytesIO(png_bytes)).convert('RGBA')
    img.save(f'{out_dir}/ic_launcher.webp', 'WEBP', lossless=True)
    img.save(f'{out_dir}/ic_launcher_round.webp', 'WEBP', lossless=True)
    print(f'  {folder}: {size}x{size} written')

print('All icons written.')
