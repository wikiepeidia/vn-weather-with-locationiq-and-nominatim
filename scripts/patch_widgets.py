import glob
import re

refresh_xml = """
    <ImageView
        android:id="@+id/widget_material_you_forecast_refresh"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_alignParentEnd="true"
        android:layout_alignParentTop="true"
        android:padding="12dp"
        android:src="@drawable/ic_sync"
        android:contentDescription="@string/action_refresh"
        android:tint="?android:attr/textColorPrimary" />
"""

files = glob.glob('app/src/main/res/layout/widget_material_you_forecast_*.xml')

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if 'widget_material_you_forecast_refresh' not in content:
        # We find the last closing tag, assuming it's </RelativeLayout>
        # but let's just find the last </.*> and insert before it if it's the root.
        # Actually in 4x2 it starts with <RelativeLayout> and has </RelativeLayout> at the end.
        
        # we can just regex search for </RelativeLayout> at the end of the file
        new_content = re.sub(r'(</RelativeLayout>\s*)$', refresh_xml + r'\n\1', content)
        if new_content == content:
            # maybe it's not RelativeLayout? Let's check
            new_content = re.sub(r'(</FrameLayout>\s*)$', refresh_xml + r'\n\1', content)
            
        with open(file, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Patched {file}")
