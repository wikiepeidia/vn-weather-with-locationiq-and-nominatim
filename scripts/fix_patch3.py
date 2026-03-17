import glob

files = glob.glob('app/src/main/res/layout/widget_material_you_*.xml')
text_to_remove = """

    <ImageView
        android:id="@+id/widget_material_you_forecast_refresh"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_alignParentEnd="true"
        android:layout_alignParentTop="true"
        android:padding="12dp"
        android:src="@drawable/ic_sync"
        android:contentDescription="@string/action_refresh"
        android:tint="?android:attr/textColorPrimary" />note
"""

text_to_remove_current = text_to_remove.replace("_forecast_refresh", "_current_refresh")

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    content = content.replace(text_to_remove, '')
    content = content.replace(text_to_remove_current, '')

    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)
