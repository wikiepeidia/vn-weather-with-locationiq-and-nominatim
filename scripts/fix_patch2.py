import glob
import re

files = glob.glob('app/src/main/res/layout/widget_material_you_*.xml')

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    # The block looks exactly like this, find and replace it
    pattern = r'\s*<ImageView\s+android:id="@+id/widget_material_you_[^"]*_refresh"[\s\S]*?/>'
    new_content = re.sub(pattern, '', content)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(new_content)
