import glob
import re

files = glob.glob('app/src/main/res/layout/widget_material_you_forecast_*.xml')

refresh_xml = """
                <ImageView
                    android:id="@+id/widget_material_you_forecast_refresh"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:layout_marginStart="4dp"
                    android:padding="6dp"
                    android:src="@drawable/ic_sync"
                    android:contentDescription="@string/action_refresh"
                    android:tint="?android:attr/textColorPrimary" />
"""

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find where the nighttimeTemperature block ends
    # We look for widget_material_you_forecast_nighttimeTemperature
    # Then the next </LinearLayout>
    
    parts = content.split('widget_material_you_forecast_nighttimeTemperature')
    if len(parts) == 1:
        print(f"Skipped {file} (no nighttimeTemperature)")
        continue
        
    subparts = parts[1].split('</LinearLayout>', 1)
    if len(subparts) == 2:
        new_content = parts[0] + 'widget_material_you_forecast_nighttimeTemperature' + subparts[0] + '</LinearLayout>\n' + refresh_xml + subparts[1]
        with open(file, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Patched {file}")

