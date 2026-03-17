import re

current_file = 'app/src/main/res/layout/widget_material_you_current.xml'
current_refresh_xml = """

    <ImageView
        android:id="@+id/widget_material_you_current_refresh"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_alignParentEnd="true"
        android:layout_alignParentBottom="true"
        android:padding="12dp"
        android:src="@drawable/ic_sync"
        android:contentDescription="@string/action_refresh"
        android:tint="?android:attr/textColorPrimary" />
</RelativeLayout>
"""

with open(current_file, 'r', encoding='utf-8') as f:
    content = f.read()

if 'widget_material_you_current_refresh' not in content:
    new_content = content.replace("</RelativeLayout>", current_refresh_xml)
    with open(current_file, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Patched current")
