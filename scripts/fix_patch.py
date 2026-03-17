import glob
import re

files = glob.glob('app/src/main/res/layout/widget_material_you_forecast_*.xml')

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    new_content = re.sub(r'<ImageView[^>]*android:id="@+id/widget_material_you_forecast_refresh"[^>]*/>\s*', '', content)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(new_content)
