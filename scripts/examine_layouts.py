import glob
import re

files = glob.glob('app/src/main/res/layout/widget_material_you_forecast_*.xml')

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    match = re.search(r'android:id="@+id/widget_material_you_forecast_currentTemperature"[\s\S]*?(</LinearLayout>)', content)
    if match:
        print(f"{file} has currentTemperature block")
    else:
        print(f"{file} DOES NOT HAVE IT")
